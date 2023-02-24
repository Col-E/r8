// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.fields;


import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.transformers.MethodTransformer;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonFinalFinalFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonFinalFinalFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("2", "2", "2");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("2", "2", "2");
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(A.class)
            .setAccessFlags(A.class.getDeclaredField("f"), AccessFlags::setFinal)
            .transform(),
        transformer(B.class)
            .setAccessFlags(B.class.getDeclaredField("f"), AccessFlags::setFinal)
            .addMethodTransformer(createRemoveObjectInitMethodTransformer())
            .transform(),
        transformer(C.class)
            .setAccessFlags(C.class.getDeclaredField("f"), AccessFlags::setFinal)
            .addMethodTransformer(createRemoveObjectInitMethodTransformer())
            .transform());
  }

  private MethodTransformer createRemoveObjectInitMethodTransformer() {
    return new MethodTransformer() {

      private boolean seenInit = false;

      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (isDefaultConstructor()) {
          if (name.equals("<init>")) {
            seenInit = true;
            return;
          }
          super.visitMethodInsn(
              opcode, owner, name.equals("init") ? "<init>" : name, descriptor, isInterface);
        } else {
          super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
      }

      @Override
      public void visitVarInsn(int opcode, int var) {
        if (isDefaultConstructor()) {
          if (seenInit) {
            super.visitVarInsn(opcode, var);
          }
        } else {
          super.visitVarInsn(opcode, var);
        }
      }

      private boolean isDefaultConstructor() {
        return getContext().getReference().getMethodName().equals("<init>")
            && getContext().getReference().getFormalTypes().isEmpty();
      }
    };
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A().f);
      System.out.println(new B().f);
      System.out.println(new C().f);
    }
  }

  @NeverClassInline
  static class A {

    /*final*/ int f;

    A() {
      this(1);
      this.f = 2;
    }

    A(int f) {
      this.f = f;
    }
  }

  @NeverClassInline
  static class B {

    /*final*/ int f;

    B() {
      this.f = 1;
      init(2); // Rewritten to B(2)
    }

    B(int f) {
      this.f = f;
    }

    private void init(int f) {}
  }

  @NeverClassInline
  static class C {

    /*final*/ int f;

    C() {
      this.f = 1;
      init(2, true); // Rewritten to B(2, true)
    }

    C(int f, boolean b) {
      if (b) {
        this.f = f;
      }
    }

    private void init(int f, boolean b) {}
  }
}
