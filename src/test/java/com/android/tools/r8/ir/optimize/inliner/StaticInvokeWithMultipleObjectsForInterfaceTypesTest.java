// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticInvokeWithMultipleObjectsForInterfaceTypesTest extends TestBase {

  @Parameter(0)
  public boolean enableInlining;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, inlining: {0}, vertical class merging: {1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(enableInlining);
    assumeFalse(enableVerticalClassMerging);
    testForRuntime(parameters)
        .addProgramClasses(I.class, J.class, A.class, B.class)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, A.class, B.class)
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        // Keep getA() and getB() to prevent that we optimize it into having static return type A/B.
        .addKeepRules("-keepclassmembers class " + Main.class.getTypeName() + " { *** get?(...); }")
        .addInliningAnnotations()
        .addOptionsModification(
            options ->
                options
                    .getOpenClosedInterfacesOptions()
                    .suppressSingleOpenInterface(Reference.classFromClass(I.class))
                    .suppressSingleOpenInterface(Reference.classFromClass(J.class)))
        .applyIf(
            enableInlining,
            R8TestBuilder::addInliningAnnotations,
            R8TestBuilder::enableInliningAnnotations)
        .applyIf(
            enableVerticalClassMerging,
            R8TestBuilder::addNoVerticalClassMergingAnnotations,
            R8TestBuilder::enableNoVerticalClassMergingAnnotations)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "0");
  }

  private static byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.startsWith("get")) {
                visitor.visitMethodInsn(opcode, owner, name, "(I)Ljava/lang/Object;", isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .setReturnType(MethodPredicate.onName("getA"), Object.class.getTypeName())
        .setReturnType(MethodPredicate.onName("getB"), Object.class.getTypeName())
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      // Transformed from `I getA(int)` to `Object getA(int)` and `J getB(int)` to
      // `Object getB(int)`.
      test(getA(args.length), getB(args.length));
    }

    // @Keep
    static /*Object*/ I getA(int f) {
      return new A(f);
    }

    // @Keep
    static /*Object*/ J getB(int f) {
      return new B(f);
    }

    @NeverInline
    static void test(I i, J j) {
      i.m();
      j.m();
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  interface I {

    void m();
  }

  @NoHorizontalClassMerging
  static class A implements I {

    int f;

    A(int f) {
      this.f = f;
    }

    @Override
    public void m() {
      System.out.println(f);
    }
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  interface J {

    void m();
  }

  @NoHorizontalClassMerging
  static class B implements J {

    int f;

    B(int f) {
      this.f = f;
    }

    @Override
    public void m() {
      System.out.println(f);
    }
  }
}
