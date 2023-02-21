// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SelfVirtualMethodAccessTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::bar");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SelfVirtualMethodAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        transformer(A.class).setPrivate(A.class.getDeclaredMethod("bar")).transform());
  }

  @Test
  public void testResolutionAccess() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(getClasses())
                .addClassProgramData(getTransformedClasses())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexProgramClass aClass =
        appInfo.definitionFor(buildType(A.class, appInfo.dexItemFactory())).asProgramClass();
    DexMethod bar = buildMethod(A.class.getDeclaredMethod("bar"), appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(bar);
    assertEquals(OptionalBool.TRUE, resolutionResult.isAccessibleFrom(aClass, appInfo));
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class A {
    /* will be private */ void bar() {
      System.out.println("A::bar");
    }

    public void foo() {
      // Virtual invoke to private method.
      bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A().foo();
    }
  }
}
