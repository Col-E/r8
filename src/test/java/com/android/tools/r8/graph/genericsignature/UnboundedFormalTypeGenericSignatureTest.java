// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnboundedFormalTypeGenericSignatureTest extends TestBase {

  private final TestParameters parameters;
  private final String SUPER_BINARY_NAME =
      DescriptorUtils.getBinaryNameFromJavaType(Super.class.getTypeName());

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnboundedFormalTypeGenericSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(Main.class).removeInnerClasses().transform(),
            transformer(Super.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(Super.class.getTypeName() + "<T>", "R", "T");
  }

  @Test
  public void testUnboundParametersInClassRuntime() throws Exception {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClassFileData(
                transformer(Main.class)
                    .removeInnerClasses()
                    .setGenericSignature("L" + SUPER_BINARY_NAME + "<TR;>;")
                    .transform(),
                transformer(Super.class).removeInnerClasses().transform())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isCfRuntime()) {
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.NullPointerException"));
    } else {
      runResult.assertSuccessWithOutputLines(Super.class.getTypeName() + "<R>", "R", "T");
    }
  }

  @Test
  public void testUnboundParametersInMethodRuntime() throws Exception {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClassFileData(
                transformer(Main.class)
                    .removeInnerClasses()
                    .setGenericSignature(
                        MethodPredicate.onName("testStatic"), "<R:Ljava/lang/Object;>()TS;")
                    .setGenericSignature(
                        MethodPredicate.onName("testVirtual"), "<R:Ljava/lang/Object;>()TQ;")
                    .transform(),
                transformer(Super.class).removeInnerClasses().transform())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isCfRuntime()) {
      runResult.assertSuccessWithOutputLines(Super.class.getTypeName() + "<T>", "null", "null");
    } else {
      runResult.assertSuccessWithOutputLines(Super.class.getTypeName() + "<T>", "S", "Q");
    }
  }

  @Test
  public void testUnboundParametersInClassR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setGenericSignature("L" + SUPER_BINARY_NAME + "<TR;>;")
                .transform(),
            transformer(Super.class).removeInnerClasses().transform())
        .addKeepAllClassesRule()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .compile()
        .apply(TestBase::verifyAllInfoFromGenericSignatureTypeParameterValidation)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "class " + Super.class.getTypeName(), "R", "class java.lang.Object");
  }

  @Test
  public void testUnboundParametersInMethodR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setGenericSignature(
                    MethodPredicate.onName("testStatic"), "<R:Ljava/lang/Object;>()TS;")
                .setGenericSignature(
                    MethodPredicate.onName("testVirtual"), "<R:Ljava/lang/Object;>()TQ;")
                .transform(),
            transformer(Super.class).removeInnerClasses().transform())
        .addKeepAllClassesRule()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .compile()
        .apply(TestBase::verifyAllInfoFromGenericSignatureTypeParameterValidation)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            Super.class.getTypeName() + "<T>", "class java.lang.Object", "class java.lang.Object");
  }

  public static class Super<T> {}

  public static class Main<T> extends Super<T> {

    public static <R extends Super<R>> void main(String[] args) {
      System.out.println(Main.class.getGenericSuperclass());
      testStatic();
      new Main<>().testVirtual();
    }

    private static <R> R testStatic() {
      try {
        Method testStatic = Main.class.getDeclaredMethod("testStatic");
        System.out.println(testStatic.getGenericReturnType());
        return null;
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    private T testVirtual() {
      try {
        Method testVirtual = Main.class.getDeclaredMethod("testVirtual");
        System.out.println(testVirtual.getGenericReturnType());
        return null;
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
