// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access.indirectfield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.resolution.access.indirectfield.pkg.C;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IndirectFieldAccessTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("42");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IndirectFieldAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public List<Class<?>> getClasses() {
    return ImmutableList.of(Main.class, A.class, B.class, C.class);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(getClasses())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexProgramClass cClass =
        appInfo.definitionFor(buildType(C.class, appInfo.dexItemFactory())).asProgramClass();
    ProgramMethod barMethod =
        cClass.lookupProgramMethod(
            buildMethod(C.class.getDeclaredMethod("bar"), appInfo.dexItemFactory()));
    DexField f =
        buildField(
            // Reflecting on B.class.getField("f") will give A.f, so manually create the reference.
            Reference.field(Reference.classFromClass(B.class), "f", Reference.INT),
            appInfo.dexItemFactory());
    FieldResolutionResult resolutionResult = appInfo.resolveField(f);
    assertTrue(resolutionResult.isSingleFieldResolutionResult());
    assertEquals(OptionalBool.TRUE, resolutionResult.isAccessibleFrom(barMethod, appView));
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  private void checkExpectedResult(TestRunResult<?> result) {
    result.assertSuccessWithOutput(EXPECTED);
  }

  /* non-public */ static class A {
    public int f = 42;
  }

  public static class B extends A {
    // Intentionally emtpy.
    // Provides access to A.f from outside this package, eg, from pkg.C::bar().
  }

  static class Main {
    public static void main(String[] args) {
      new C().bar();
    }
  }
}
