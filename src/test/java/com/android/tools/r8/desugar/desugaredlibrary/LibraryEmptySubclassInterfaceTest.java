// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryEmptySubclassInterfaceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public LibraryEmptySubclassInterfaceTest(
      boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(LibraryEmptySubclassInterfaceTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getResult());
    assertExpectedKeepRules(keepRuleConsumer);
  }

  private void assertExpectedKeepRules(KeepRuleConsumer keepRuleConsumer) {
    if (!requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      return;
    }
    String keepRules = keepRuleConsumer.get();
    assertThat(keepRules, containsString("-keep class j$.util.Map"));
    assertThat(keepRules, containsString("-keep class j$.util.concurrent.ConcurrentHashMap"));
    assertThat(keepRules, containsString("-keep class j$.util.concurrent.ConcurrentMap"));
  }

  @Test
  public void testR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addInnerClasses(LibraryEmptySubclassInterfaceTest.class)
        .addKeepMainRule(Executor.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines(getResult());
    assertExpectedKeepRules(keepRuleConsumer);
  }

  private String getResult() {
    return requiresEmulatedInterfaceCoreLibDesugaring(parameters)
        ? "class j$.util.concurrent.ConcurrentHashMap"
        : "class java.util.concurrent.ConcurrentHashMap";
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {

    public static void main(String[] args) {
      System.out.println(NullableConcurrentHashMap.class.getSuperclass());
    }
  }

  static class NullableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    NullableConcurrentHashMap() {
      super();
    }
  }
}
