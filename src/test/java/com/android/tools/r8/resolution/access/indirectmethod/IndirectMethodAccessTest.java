// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access.indirectmethod;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.access.indirectmethod.pkg.C;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IndirectMethodAccessTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IndirectMethodAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public List<Class<?>> getClasses() {
    return ImmutableList.of(Main.class, A.class, C.class);
  }

  private List<byte[]> getTransforms() throws IOException {
    return ImmutableList.of(
        // Compilation with javac will generate a synthetic bridge for foo. Remove it.
        transformer(B.class)
            .removeMethods((access, name, descriptor, signature, exceptions) -> name.equals("foo"))
            .transform());
  }

  @Test
  public void testResolutionAccess() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(getClasses())
                .addClassProgramData(getTransforms())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexProgramClass cClass =
        appInfo.definitionFor(buildType(C.class, appInfo.dexItemFactory())).asProgramClass();
    DexMethod bar = buildMethod(B.class.getMethod("foo"), appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(bar);
    assertEquals(
        OptionalBool.TRUE, resolutionResult.isAccessibleForVirtualDispatchFrom(cClass, appView));
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransforms())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransforms())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  private void checkExpectedResult(TestRunResult<?> result) {
    result.assertSuccessWithOutput(EXPECTED);
  }

  /* non-public */ static class A {
    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class B extends A {
    // Intentionally emtpy.
    // Provides access to A.foo from outside this package, eg, from pkg.C::bar().
  }

  static class Main {
    public static void main(String[] args) {
      new C().bar();
    }
  }
}
