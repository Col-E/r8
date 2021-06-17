// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomMapHierarchyTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED =
      StringUtils.lines("B::getOrDefault", "default 1", "B::getOrDefault", "default 2");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public CustomMapHierarchyTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  @Test
  public void testWithoutLibraryDesugaring() throws Exception {
    Assume.assumeTrue(
        parameters.getRuntime().isCf()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport()));
    testForRuntime(parameters)
        .addInnerClasses(CustomMapHierarchyTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(CustomMapHierarchyTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8Cf2Cf() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);

    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(CustomMapHierarchyTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setIncludeClassesChecksum(true)
            .setMinApi(parameters.getApiLevel())
            .allowStdoutMessages()
            .compile()
            .writeToZip();
    String desugaredLibraryKeepRules = "";
    if (shrinkDesugaredLibrary && keepRuleConsumer.get() != null) {
      // Collection keep rules is only implemented in the DEX writer.
      assertEquals(0, keepRuleConsumer.get().length());
      desugaredLibraryKeepRules = "-keep class * { *; }";
    }
    if (parameters.getRuntime().isDex()) {
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .allowStdoutMessages()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary,
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(getDesugaredLibraryInCF(parameters, options -> {}))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED);
    }
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(CustomMapHierarchyTest.class)
        .addKeepMainRule(Main.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(new B<String>().getOrDefault("Not found", "default 1"));
      System.out.println(
          ((Map<String, String>) new B<String>()).getOrDefault("Not found", "default 2"));
    }
  }

  abstract static class A<T> extends LinkedHashMap<T, T> {}

  // AbstractSequentialList implements List further up the hierarchy.
  static class B<T> extends A<T> {
    // Need at least one overridden default method for emulated dispatch.
    @Override
    public T getOrDefault(Object key, T defaultValue) {
      System.out.println("B::getOrDefault");
      return super.getOrDefault(key, defaultValue);
    }
  }
}
