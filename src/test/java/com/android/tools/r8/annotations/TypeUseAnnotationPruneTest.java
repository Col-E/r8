// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.annotations.testclasses.MainWithTypeAndGeneric;
import com.android.tools.r8.annotations.testclasses.NotNullTestClass;
import com.android.tools.r8.annotations.testclasses.NotNullTestRuntime;
import com.android.tools.r8.annotations.testclasses.SuperInterface;
import com.android.tools.r8.annotations.testclasses.TestClassWithTypeAndGenericAnnotations;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundAnnotationSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.AnnotatedType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeUseAnnotationPruneTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private String getExpected(String notNullTestRuntimeTypeName) {
    return StringUtils.joinLines(
        "printAnnotation - Class: " + notNullTestRuntimeTypeName,
        "printAnnotation - Class: NULL",
        "printAnnotation - Field: NULL",
        "printAnnotation - Field: NULL",
        "printAnnotation - Method: NULL",
        "printAnnotation - Method: NULL",
        "Hello World!");
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
        .addKeepRuntimeVisibleParameterAnnotations()
        .addKeepRuntimeInvisibleParameterAnnotations()
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
              ClassSubject clazz = inspector.clazz(TestClassWithTypeAndGenericAnnotations.class);
              assertThat(clazz, isPresent());
              assertTrue(
                  clazz.annotations().stream().noneMatch(FoundAnnotationSubject::isTypeAnnotation));
              FieldSubject field = clazz.uniqueFieldWithOriginalName("field");
              assertThat(field, isPresent());
              assertEquals(0, field.annotations().size());
              MethodSubject method = clazz.uniqueMethodWithOriginalName("method");
              assertThat(method, isPresent());
              // We create a dex annotation for the checked exception.
              assertEquals(1, method.annotations().size());
            },
            options -> options.programConsumer = ClassFileConsumer.emptyConsumer())
        .run(parameters.getRuntime(), MainWithTypeAndGeneric.class)
        .assertFailureWithErrorThatThrowsIf(parameters.isDexRuntime(), NoSuchMethodError.class)
        .assertSuccessWithOutputLinesIf(
            parameters.isCfRuntime(), getExpected(typeName(NotNullTestRuntime.class)));
  }
}
