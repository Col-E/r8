// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate.onName;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrivateOverrideOfPublicMethodTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("A", "B", "IAE", "A", "B", "A");
  private static final String EXPECTED_OUTPUT_5_TO_6 =
      StringUtils.lines("A", "A", "A", "A", "A", "A");
  private static final String EXPECTED_OUTPUT_7 = StringUtils.lines("A", "B", "A", "A", "B", "A");

  private static byte[] programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws IOException {
    programClassFileData =
        transformer(B.class)
            .renameMethod(onName("bar"), "foo")
            .transformMethodInsnInMethod(
                "<init>",
                (opcode, owner, name, descriptor, isInterface, continuation) ->
                    continuation.visitMethodInsn(
                        opcode, owner, name.equals("bar") ? "foo" : name, descriptor, isInterface))
            .transform();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V5_1_1)
                && parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V6_0_1),
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT_5_TO_6),
            parameters.isDexRuntimeVersion(Version.V7_0_0),
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT_7),
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {

    public static void main(String[] args) {
      new A().foo();
      try {
        new B().foo();
      } catch (IllegalAccessError e) {
        System.out.println("IAE");
      }
      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      a.foo();
      A b = System.currentTimeMillis() > 0 ? new B() : new A();
      b.foo();
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void foo() {
      System.out.println("A");
    }
  }

  static class B extends A {

    B() {
      if (System.currentTimeMillis() > 0) {
        bar();
      }
    }

    // Renamed to foo.
    @NeverInline
    private void bar() {
      System.out.println("B");
    }
  }
}
