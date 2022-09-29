// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression for b/247146910. */
@RunWith(Parameterized.class)
public class EnumInEnumFieldTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumInEnumFieldTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumInEnumFieldTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    // TODO(b/247146910): Should not throw compilation error.
    assertThrows(CompilationFailedException.class, setupTest()::compile);
  }

  @Test
  public void testEnumUnboxingAllowingTypeErrors() throws Exception {
    // TODO(b/247146910): Should not insert throw null.
    setupTest()
        .addOptionsModification(options -> options.testing.allowTypeErrors = true)
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertAllWarningsMatch(
                    DiagnosticsMatcher.diagnosticMessage(
                        containsString(
                            "does not type check and will be assumed to be unreachable."))))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  private R8FullTestBuilder setupTest() throws Exception {
    return testForR8(parameters.getBackend())
        .addInnerClasses(EnumInEnumFieldTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(MyEnum.class, OtherEnum.class))
        .addNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel());
  }

  @NeverClassInline
  public enum OtherEnum {
    C,
    D;
  }

  @NeverClassInline
  public enum MyEnum {
    A,
    B;

    public OtherEnum otherEnum;
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      set(System.currentTimeMillis() > 0 ? OtherEnum.C : OtherEnum.D);
      System.out.println(MyEnum.A.otherEnum.ordinal());
    }

    public static void set(OtherEnum otherEnum) {
      MyEnum.A.otherEnum = otherEnum;
    }
  }
}
