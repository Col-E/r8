// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepDeserializeLambdaMethodTestRunner extends TestBase {

  private static final Class<?> TEST_CLASS_CF =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestCf.class;
  private static final Class<?> TEST_CLASS_DEX =
      com.android.tools.r8.cf.KeepDeserializeLambdaMethodTestDex.class;

  private static final String EXPECTED =
      StringUtils.lines("base lambda", KeepDeserializeLambdaMethodTest.LAMBDA_MESSAGE);

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public KeepDeserializeLambdaMethodTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<?> getMainClass() {
    return parameters.isCfRuntime() ? TEST_CLASS_CF : TEST_CLASS_DEX;
  }

  private List<Class<?>> getClasses() {
    return ImmutableList.of(KeepDeserializeLambdaMethodTest.class, getMainClass());
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkPresenceOfDeserializedLambdas);
  }

  @Test
  public void testDontKeepDeserializeLambdaR8() throws Exception {
    test(false);
  }

  @Test
  public void testKeepDeserializedLambdaR8() throws Exception {
    test(true);
  }

  private void test(boolean addKeepDeserializedLambdaRule)
      throws IOException, CompilationFailedException, ExecutionException {
    R8CompatTestBuilder builder =
        testForR8Compat(parameters.getBackend())
            .addProgramClasses(getClasses())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(getMainClass())
            .addOptionsModification(
                options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces());
    if (addKeepDeserializedLambdaRule) {
      builder.allowUnusedProguardConfigurationRules(parameters.isDexRuntime());
      builder.addKeepRules(
          "-keepclassmembers class * {",
          "private static synthetic java.lang.Object "
              + "$deserializeLambda$(java.lang.invoke.SerializedLambda);",
          "}",
          // TODO(b/148836254): Support deserialized lambdas without the need of additional rules.
          "-keep class * { private static synthetic void lambda$*(); }");
    } else {
      builder.addDontObfuscate().noTreeShaking();
    }
    builder
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkPresenceOfDeserializedLambdas);
  }

  private void checkPresenceOfDeserializedLambdas(CodeInspector inspector) {
    for (Class<?> clazz : getClasses()) {
      MethodSubject method =
          inspector.clazz(clazz).uniqueMethodWithOriginalName("$deserializeLambda$");
      assertEquals(
          "Unexpected status for $deserializedLambda$ on " + clazz.getSimpleName(),
          parameters.isCfRuntime(),
          method.isPresent());
    }
  }
}
