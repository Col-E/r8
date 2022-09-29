// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a variant of a regression test for b/247146910 where the instance field on the enum is
 * unrelated to enum unboxing.
 */
@RunWith(Parameterized.class)
public class EnumInstanceFieldReferenceInsideAndOutsideTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumInstanceFieldReferenceInsideAndOutsideTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumInstanceFieldReferenceInsideAndOutsideTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    // TODO(b/247146910): Should not throw.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(EnumInstanceFieldReferenceInsideAndOutsideTest.class)
                .addKeepMainRule(Main.class)
                .addKeepRules(enumKeepRules.getKeepRules())
                .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
                .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
                .addNeverClassInliningAnnotations()
                .setMinApi(parameters.getApiLevel())
                .addOptionsModification(options -> options.testing.allowTypeErrors = true)
                .allowDiagnosticWarningMessages()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorMessageThatMatches(
                            containsString(
                                "Enum "
                                    + typeName(MyEnum.class)
                                    + " has been unboxed but is still in the program"))));
  }

  private boolean hasVerifyError() {
    return parameters.getDexRuntimeVersion().isDalvik()
        || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V12_0_0);
  }

  @Test
  public void testEnumUnboxingAllowNotPrunedEnums() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumInstanceFieldReferenceInsideAndOutsideTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(
            opt -> {
              enableEnumOptions(opt, enumValueOptimization);
              opt.testing.allowNotPrunedUnboxedEnums = true;
            })
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .addNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.testing.allowTypeErrors = true)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/247146910): Should not fail with runtime error.
        .assertFailureWithErrorThatThrowsIf(hasVerifyError(), VerifyError.class)
        .assertFailureWithErrorThatThrowsIf(!hasVerifyError(), NoSuchFieldError.class);
  }

  @NeverClassInline
  public enum MyEnum {
    A(10),
    B(20);

    public int instanceValue;

    MyEnum(int instanceValue) {
      this.instanceValue = instanceValue;
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      set(System.currentTimeMillis() > 0 ? 42 : 0);
      System.out.println(MyEnum.B.instanceValue);
    }

    public static void set(int newValue) {
      MyEnum.B.instanceValue = newValue;
    }
  }
}
