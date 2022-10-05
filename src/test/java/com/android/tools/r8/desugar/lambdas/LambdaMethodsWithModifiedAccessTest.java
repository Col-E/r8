// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.utils.codeinspector.Matchers.isNative;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.function.Function;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// See b/234475018) for context.
@RunWith(Parameterized.class)
public class LambdaMethodsWithModifiedAccessTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String LAMBDA_TO_PUBLIC = "lambda$withPublicLambdaMethod$0";
  private static final String LAMBDA_TO_NATIVE = "lambda$withNativeLambdaMethod$1";
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("withPublicLambdaMethod", "UnsatisfiedLinkError: withNativeLambdaMethod");

  @BeforeClass
  public static void checkJavacLambdas() throws IOException {
    CodeInspector inspector =
        new CodeInspector(ToolHelper.getClassFileForTestClass(LambdaTest.class));
    inspector.forAllClasses(clazz -> clazz.forAllMethods(System.out::println));
    assertThat(
        inspector.clazz(LambdaTest.class).uniqueMethodWithOriginalName(LAMBDA_TO_PUBLIC),
        isPrivate());
    assertThat(
        inspector.clazz(LambdaTest.class).uniqueMethodWithOriginalName(LAMBDA_TO_NATIVE),
        isPrivate());
  }

  private void inspect(CodeInspector inspector) {
    assertThat(
        inspector.clazz(LambdaTest.class).uniqueMethodWithOriginalName(LAMBDA_TO_PUBLIC),
        isPublic());
    assertThat(
        inspector.clazz(LambdaTest.class).uniqueMethodWithOriginalName(LAMBDA_TO_NATIVE),
        isNative());
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getTransformedLambdaTest())
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::inspect)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  // TODO(b/234475018): Compilation hits an AssertionError which checks expected javac generated
  // lambda methods in LambdaDescriptor.lookupTargetMethod.
  // assert target == null
  //     || (implHandle.type.isInvokeInstance() && isInstanceMethod(target))
  //     || (implHandle.type.isInvokeDirect() && isPrivateInstanceMethod(target))
  //     || (implHandle.type.isInvokeDirect() && isPublicizedInstanceMethod(target));
  @Test(expected = CompilationFailedException.class)
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getTransformedLambdaTest())
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  // TODO(b/234475018): Compilation hits an AssertionError which checks expected javac generated
  // lambda methods in LambdaDescriptor.lookupTargetMethod.
  // assert target == null
  //     || (implHandle.type.isInvokeInstance() && isInstanceMethod(target))
  //     || (implHandle.type.isInvokeDirect() && isPrivateInstanceMethod(target))
  //     || (implHandle.type.isInvokeDirect() && isPublicizedInstanceMethod(target));
  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getTransformedLambdaTest())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  private byte[] getTransformedLambdaTest() throws Exception {
    return transformer(LambdaTest.class)
        .setAccessFlags(MethodPredicate.onName(LAMBDA_TO_NATIVE), MethodAccessFlags::setNative)
        .setAccessFlags(MethodPredicate.onName(LAMBDA_TO_PUBLIC), AccessFlags::promoteToPublic)
        .removeMethodsCodeAndAnnotations(MethodPredicate.onName(LAMBDA_TO_NATIVE))
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      new LambdaTest().withPublicLambdaMethod().apply(null);
      try {
        new LambdaTest().withNativeLambdaMethod().apply(null);
      } catch (UnsatisfiedLinkError e) {
        if (e.getMessage().contains(LAMBDA_TO_NATIVE)) {
          System.out.println("UnsatisfiedLinkError: withNativeLambdaMethod");
        } else {
          System.out.println("UnsatisfiedLinkError: with unexpected content");
        }
      }
    }
  }

  public static class LambdaTest {
    String f;

    Function<Void, String> withPublicLambdaMethod() {
      return (ignored) -> {
        System.out.println("withPublicLambdaMethod");
        return f;
      };
    }

    Function<Void, String> withNativeLambdaMethod() {
      return (ignored) -> f;
    }
  }
}
