// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumMissingFieldsUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumMissingFieldsUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getEnumProgramData())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(CompilationEnum.class))
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchFieldError"))
        .inspectStdOut(this::assertLines2By2Correct);
  }

  private byte[] getEnumProgramData() throws IOException {
    return transformer(RuntimeEnum.class)
        .setClassDescriptor(descriptor(CompilationEnum.class))
        .transform();
  }

  // CompilationEnum is used for the compilation of TestClass.
  @NeverClassInline
  public enum CompilationEnum {
    A,
    B,
    C,
    D
  }

  // CompilationEnum is used for the runtime execution of TestClass.
  @NeverClassInline
  public enum RuntimeEnum {
    A,
    D
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(CompilationEnum.A.ordinal());
      System.out.println(0);
      // The field C will be missing at runtime.
      System.out.println(CompilationEnum.C.ordinal());
      System.out.println(2);
    }
  }
}
