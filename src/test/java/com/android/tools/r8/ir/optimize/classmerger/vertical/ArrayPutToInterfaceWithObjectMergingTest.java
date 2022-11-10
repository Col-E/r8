// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classmerger.vertical;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
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
public class ArrayPutToInterfaceWithObjectMergingTest extends TestBase {

  @Parameter(0)
  public boolean enableVerticalClassMerging;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, vertical class merging: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(enableVerticalClassMerging);
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        .addKeepMethodRules(Reference.methodFromMethod(Main.class.getDeclaredMethod("get")))
        .addOptionsModification(
            options ->
                options
                    .getOpenClosedInterfacesOptions()
                    .suppressSingleOpenInterface(Reference.classFromClass(I.class)))
        .applyIf(
            enableVerticalClassMerging,
            R8TestBuilder::addNoVerticalClassMergingAnnotations,
            R8TestBuilder::enableNoVerticalClassMergingAnnotations)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (enableVerticalClassMerging) {
                inspector.assertMergedIntoSubtype(I.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  private static byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("get")) {
                visitor.visitMethodInsn(opcode, owner, name, "()Ljava/lang/Object;", isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .setReturnType(MethodPredicate.onName("get"), Object.class.getTypeName())
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      I[] is = new I[1];
      // Transformed from `I get()` to `Object get()`.
      is[0] = get();
      print(is);
    }

    // @Keep
    static /*Object*/ I get() {
      return new A();
    }

    @NeverInline
    static void print(I[] is) {
      System.out.println(is[0]);
    }
  }

  @NoVerticalClassMerging
  interface I {}

  static class A implements I {

    @Override
    public String toString() {
      return "A";
    }
  }
}
