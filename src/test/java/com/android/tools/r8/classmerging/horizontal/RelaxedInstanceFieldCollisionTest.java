// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

/***
 * This is a regression test for b/263934503.
 */
@RunWith(Parameterized.class)
public class RelaxedInstanceFieldCollisionTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final String[] EXPECTED =
      new String[] {"UnrelatedA", "UnrelatedB", "UnrelatedC", "UnrelatedD"};

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(UnrelatedA.class, UnrelatedB.class, UnrelatedC.class, UnrelatedD.class)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(UnrelatedA.class, UnrelatedB.class, UnrelatedC.class, UnrelatedD.class)
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(UnrelatedA.class, UnrelatedB.class, UnrelatedC.class, UnrelatedD.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesMerged(A.class, B.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private Collection<byte[]> getTransformedClasses() throws Exception {
    return Arrays.asList(
        transformer(A.class)
            .transformFieldInsnInMethod("<init>", RelaxedInstanceFieldCollisionTest::visitFieldInsn)
            .renameField(FieldPredicate.onName("field2"), "field")
            .transform(),
        transformer(B.class)
            .transformFieldInsnInMethod("<init>", RelaxedInstanceFieldCollisionTest::visitFieldInsn)
            .renameField(FieldPredicate.onName("field2"), "field")
            .transform(),
        transformer(Main.class)
            .transformFieldInsnInMethod("main", RelaxedInstanceFieldCollisionTest::visitFieldInsn)
            .transform());
  }

  private static void visitFieldInsn(
      int opcode, String owner, String name, String descriptor, MethodVisitor visitor) {
    visitor.visitFieldInsn(opcode, owner, name.equals("field2") ? "field" : name, descriptor);
  }

  public static class UnrelatedA {

    @Override
    public String toString() {
      return "UnrelatedA";
    }
  }

  public static class UnrelatedB {

    @Override
    public String toString() {
      return "UnrelatedB";
    }
  }

  public static class UnrelatedC {

    @Override
    public String toString() {
      return "UnrelatedC";
    }
  }

  public static class UnrelatedD {

    @Override
    public String toString() {
      return "UnrelatedD";
    }
  }

  public static class A {

    public UnrelatedA field;
    public UnrelatedB field2; /* will be renamed field */

    public A(UnrelatedA unrelatedA, UnrelatedB unrelatedB) {
      field = unrelatedA;
      field2 = unrelatedB;
    }
  }

  public static class B {

    public UnrelatedC field;
    public UnrelatedD field2; /* will be renamed field */

    public B(UnrelatedC unrelatedA, UnrelatedD unrelatedB) {
      field = unrelatedA;
      field2 = unrelatedB;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() > 0) {
        A a = new A(new UnrelatedA(), new UnrelatedB());
        System.out.println(a.field);
        System.out.println(a.field2);
      }
      if (System.currentTimeMillis() > 0) {
        B b = new B(new UnrelatedC(), new UnrelatedD());
        System.out.println(b.field);
        System.out.println(b.field2);
      }
    }
  }
}
