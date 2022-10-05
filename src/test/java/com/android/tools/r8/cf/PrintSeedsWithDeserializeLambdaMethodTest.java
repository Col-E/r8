// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrintSeedsWithDeserializeLambdaMethodTest extends TestBase {

  private static final Class<?> TEST_CLASS_CF = KeepDeserializeLambdaMethodTestCf.class;
  private static final Class<?> TEST_CLASS_DEX = KeepDeserializeLambdaMethodTestDex.class;

  private static final String EXPECTED =
      StringUtils.lines("base lambda", KeepDeserializeLambdaMethodTest.LAMBDA_MESSAGE);

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public PrintSeedsWithDeserializeLambdaMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<?> getMainClass() {
    return parameters.isCfRuntime() ? TEST_CLASS_CF : TEST_CLASS_DEX;
  }

  private List<Class<?>> getClasses() {
    return ImmutableList.of(KeepDeserializeLambdaMethodTest.class, getMainClass());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(getClasses())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(getMainClass())
        // TODO(b/214496607): Improve analysis precision to ensure there are no open interfaces.
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .addPrintSeeds()
        .allowStdoutMessages()
        .addDontObfuscate()
        .noTreeShaking()
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
