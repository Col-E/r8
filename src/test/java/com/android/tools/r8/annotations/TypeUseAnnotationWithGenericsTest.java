// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.annotations.testclasses.MainWithTypeAndGeneric;
import com.android.tools.r8.annotations.testclasses.NotNullTestClass;
import com.android.tools.r8.annotations.testclasses.NotNullTestRuntime;
import com.android.tools.r8.annotations.testclasses.SuperInterface;
import com.android.tools.r8.annotations.testclasses.TestClassWithTypeAndGenericAnnotations;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundAnnotationSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.AnnotatedType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeUseAnnotationWithGenericsTest extends TestBase {

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
        .assertSuccessWithOutputLines(getExpected(typeName(NotNullTestRuntime.class)));
  }

  private String getExpected(String notNullTestRuntimeTypeName) {
    return StringUtils.joinLines(
        "printAnnotation - Class: " + notNullTestRuntimeTypeName,
        "printAnnotation - Class: NULL",
        "printAnnotation - Extends(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - Implements(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - Field: NULL",
        "printAnnotation - Field: NULL",
        "printAnnotation - Field(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - Method: NULL",
        "printAnnotation - Method: NULL",
        "printAnnotation - MethodReturnType(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - MethodParameter at 0(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - MethodParameter at 1(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - MethodException at 0(0): " + notNullTestRuntimeTypeName,
        "printAnnotation - MethodException at 1(0): " + notNullTestRuntimeTypeName,
        "Hello World!");
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
    setupR8Test(
        builder ->
            builder.addKeepClassRules(
                NotNullTestClass.class, NotNullTestRuntime.class, SuperInterface.class));
  }

  @Test
  public void testR8WithRenaming() throws Exception {
    setupR8Test(
        builder ->
            builder.addKeepClassRulesWithAllowObfuscation(
                NotNullTestClass.class, NotNullTestRuntime.class, SuperInterface.class));
  }

  private void setupR8Test(ThrowableConsumer<R8FullTestBuilder> modifier) throws Exception {
    Box<String> finalNotNullTestRuntimeName = new Box<>();
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MainWithTypeAndGeneric.class,
            NotNullTestClass.class,
            NotNullTestRuntime.class,
            TestClassWithTypeAndGenericAnnotations.class,
            SuperInterface.class)
        .setMinApi(parameters)
        .apply(modifier)
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRuntimeInvisibleAnnotations()
        .addKeepRuntimeVisibleTypeAnnotations()
        .addKeepRuntimeInvisibleTypeAnnotations()
        .addKeepAttributeSignature()
        .addKeepAttributeExceptions()
        .addKeepMainRule(MainWithTypeAndGeneric.class)
        .addKeepClassAndMembersRules(TestClassWithTypeAndGenericAnnotations.class)
        .applyIf(parameters.isDexRuntime(), b -> b.addDontWarn(AnnotatedType.class))
        .compile()
        .inspectWithOptions(
            inspector -> {
              ClassSubject notNullTestClass = inspector.clazz(NotNullTestClass.class);
              assertThat(notNullTestClass, isPresent());
              ClassSubject notNullTestRuntime = inspector.clazz(NotNullTestRuntime.class);
              assertThat(notNullTestRuntime, isPresent());
              finalNotNullTestRuntimeName.set(notNullTestRuntime.getFinalName());
              ClassSubject clazz = inspector.clazz(TestClassWithTypeAndGenericAnnotations.class);
              assertThat(clazz, isPresent());
              if (parameters.isDexRuntime()) {
                inspectAnnotations(
                    clazz.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    2,
                    0,
                    1,
                    1);
              } else {
                inspectAnnotations(
                    clazz.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    10,
                    8,
                    5,
                    5);
              }
              FieldSubject field = clazz.uniqueFieldWithOriginalName("field");
              assertThat(field, isPresent());
              if (parameters.isDexRuntime()) {
                inspectAnnotations(
                    field.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    0,
                    0,
                    0,
                    0);
              } else {
                inspectAnnotations(
                    field.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    4,
                    4,
                    2,
                    2);
              }
              MethodSubject method = clazz.uniqueMethodWithOriginalName("method");
              assertThat(method, isPresent());
              // We create a dex annotation for the checked exception.
              if (parameters.isDexRuntime()) {
                inspectAnnotations(
                    method.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    1,
                    0,
                    0,
                    0);
              } else {
                inspectAnnotations(
                    method.annotations(),
                    notNullTestRuntime.getFinalReference(),
                    notNullTestClass.getFinalReference(),
                    17,
                    16,
                    8,
                    8);
              }
            },
            options -> options.programConsumer = ClassFileConsumer.emptyConsumer())
        .run(parameters.getRuntime(), MainWithTypeAndGeneric.class)
        .assertFailureWithErrorThatThrowsIf(parameters.isDexRuntime(), NoSuchMethodError.class)
        .assertSuccessWithOutputLinesIf(
            parameters.isCfRuntime(), getExpected(finalNotNullTestRuntimeName.get()));
  }

  private void inspectAnnotations(
      List<FoundAnnotationSubject> annotations,
      ClassReference notNullRuntime,
      ClassReference notNullClass,
      int expectedCount,
      int expectedTypeAnnotationCount,
      int expectedNotNullTestRuntimeCount,
      int expectedNotNullTestClassCount) {
    assertEquals(expectedCount, annotations.size());
    assertEquals(
        expectedTypeAnnotationCount,
        annotations.stream().filter(FoundAnnotationSubject::isTypeAnnotation).count());
    assertEquals(
        expectedNotNullTestRuntimeCount,
        annotations.stream()
            .filter(
                annotation ->
                    annotation.getAnnotation().type.asClassReference().equals(notNullRuntime))
            .count());
    assertEquals(
        expectedNotNullTestClassCount,
        annotations.stream()
            .filter(
                annotation ->
                    annotation.getAnnotation().type.asClassReference().equals(notNullClass))
            .count());
  }
}
