// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.annotations.testclasses.MainWithTypeAndGeneric;
import com.android.tools.r8.annotations.testclasses.NotNullTestClass;
import com.android.tools.r8.annotations.testclasses.NotNullTestRuntime;
import com.android.tools.r8.annotations.testclasses.SuperInterface;
import com.android.tools.r8.annotations.testclasses.TestClassWithTypeAndGenericAnnotations;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.AnnotatedType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeUseAnnotationWithGenericsTest extends TestBase {

  private final String EXPECTED_JVM =
      StringUtils.joinLines(
          "printAnnotation - Class: " + typeName(NotNullTestRuntime.class),
          "printAnnotation - Class: NULL",
          "printAnnotation - Extends(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - Implements(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - Field: NULL",
          "printAnnotation - Field: NULL",
          "printAnnotation - Field(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - Method: NULL",
          "printAnnotation - Method: NULL",
          "printAnnotation - MethodReturnType(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - MethodParameter at 0(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - MethodParameter at 1(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - MethodException at 0(0): " + typeName(NotNullTestRuntime.class),
          "printAnnotation - MethodException at 1(0): " + typeName(NotNullTestRuntime.class),
          "Hello World!");

  private final String EXPECTED_R8 =
      StringUtils.joinLines(
          "printAnnotation - Class: " + typeName(NotNullTestRuntime.class),
          "printAnnotation - Class: NULL",
          "printAnnotation - Field: NULL",
          "printAnnotation - Field: NULL",
          "printAnnotation - Method: NULL",
          "printAnnotation - Method: NULL",
          "Hello World!");

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addProgramClasses(
            MainWithTypeAndGeneric.class,
            NotNullTestClass.class,
            NotNullTestRuntime.class,
            TestClassWithTypeAndGenericAnnotations.class,
            SuperInterface.class)
        .run(parameters.getRuntime(), MainWithTypeAndGeneric.class)
        .assertSuccessWithOutputLines(EXPECTED_JVM);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(
            MainWithTypeAndGeneric.class,
            NotNullTestClass.class,
            NotNullTestRuntime.class,
            TestClassWithTypeAndGenericAnnotations.class,
            SuperInterface.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainWithTypeAndGeneric.class)
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MainWithTypeAndGeneric.class,
            NotNullTestClass.class,
            NotNullTestRuntime.class,
            TestClassWithTypeAndGenericAnnotations.class,
            SuperInterface.class)
        .setMinApi(parameters)
        .addKeepClassRules(NotNullTestClass.class, NotNullTestRuntime.class, SuperInterface.class)
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRuntimeInvisibleAnnotations()
        .addKeepRuntimeVisibleTypeAnnotations()
        .addKeepRuntimeInvisibleTypeAnnotations()
        .addKeepAttributeSignature()
        .addKeepMainRule(MainWithTypeAndGeneric.class)
        .addKeepClassAndMembersRules(TestClassWithTypeAndGenericAnnotations.class)
        .applyIf(parameters.isDexRuntime(), b -> b.addDontWarn(AnnotatedType.class))
        .compile()
        .inspect(
            inspector -> {
              // TODO(b/271543766): Add inspection of annotated types, even runtime invisible.
            })
        .run(parameters.getRuntime(), MainWithTypeAndGeneric.class)
        .assertFailureWithErrorThatThrowsIf(parameters.isDexRuntime(), NoSuchMethodError.class)
        .assertSuccessWithOutputLinesIf(parameters.isCfRuntime(), EXPECTED_R8);
  }
}
