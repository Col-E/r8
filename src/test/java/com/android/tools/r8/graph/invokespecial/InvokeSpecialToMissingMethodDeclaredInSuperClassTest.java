// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToMissingMethodDeclaredInSuperClassTest extends TestBase {

  @Parameter(0)
  public boolean testHorizontalClassMerging;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, horizontal: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(testHorizontalClassMerging);
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class, MergeIntoB.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .applyIf(
            testHorizontalClassMerging,
            testBuilder ->
                testBuilder
                    .addProgramClasses(MainWithHorizontalClassMerging.class, MergeIntoB.class)
                    .addKeepMainRule(MainWithHorizontalClassMerging.class)
                    .addHorizontallyMergedClassesInspector(
                        inspector -> {
                          if (testHorizontalClassMerging) {
                            inspector.assertMergedInto(MergeIntoB.class, B.class);
                          }
                          inspector.assertNoOtherClassesMerged();
                        }))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals("notify", name);
              continuation.visitMethodInsn(
                  INVOKESPECIAL, binaryName(B.class), "foo", descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("A.foo()");
    }
  }

  public static class B extends A {

    public void bar() {
      // Will be rewritten to invoke-special B.foo() which is missing (except when testing
      // horizontal class merging), but found in A.
      notify();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().bar();
    }
  }

  // Extra program inputs when testing horizontal class merging.

  public static class MergeIntoB extends A {

    public void foo() {
      System.out.println("MergeIntoB.foo()");
    }
  }

  public static class MainWithHorizontalClassMerging {

    public static void main(String[] args) {
      new MergeIntoB().foo();
    }
  }
}
