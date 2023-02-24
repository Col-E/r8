// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureCorrectnessHelperTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenericSignatureCorrectnessHelperTests(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testAllValid() throws Exception {
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            buildInnerClasses(GenericSignatureCorrectnessHelperTests.class)
                .addLibraryFile(ToolHelper.getJava8RuntimeJar())
                .build(),
            factory -> {
              ProguardConfiguration.Builder builder =
                  ProguardConfiguration.builder(
                      factory, new Reporter(new TestDiagnosticMessagesImpl()));
              builder.addKeepAttributePatterns(ImmutableList.of(ProguardKeepAttributes.SIGNATURE));
              return builder.build();
            });
    GenericSignatureContextBuilder contextBuilder = GenericSignatureContextBuilder.create(appView);
    GenericSignatureCorrectnessHelper.createForVerification(appView, contextBuilder)
        .run(appView.appInfo().classes());
  }

  @Test
  public void testMissingTypeArgumentInClassBound() throws Exception {
    runTest(
        ImmutableList.of(Base.class),
        ImmutableList.of(
            transformer(ClassWithClassBound.class)
                .setGenericSignature(
                    existing -> {
                      // Replace the generic type parameter T with R.
                      return existing.replace("<T:", "<R:");
                    })
                .transform()),
        ClassWithClassBound.class,
        SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED);
  }

  @Test
  public void testMissingTypeArgumentInInterfaceBound() throws Exception {
    runTest(
        ImmutableList.of(I.class, J.class),
        ImmutableList.of(
            transformer(ClassWithInterfaceBound.class)
                .setGenericSignature(
                    existing -> {
                      // Replace the generic type parameter T with R.
                      return existing.replace("<T:", "<R:");
                    })
                .transform()),
        ClassWithInterfaceBound.class,
        SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED);
  }

  @Test
  public void testMembersHavingInvalidTypeReference() throws Exception {
    runTest(
        ImmutableList.of(),
        ImmutableList.of(
            transformer(ClassWithMembersHavingInvalidTypeReference.class)
                .setGenericSignature(
                    existing -> {
                      // Replace the generic type parameter T with R.
                      return existing.replace("<T:", "<R:");
                    })
                .transform()),
        ClassWithMembersHavingInvalidTypeReference.class,
        SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED);
  }

  @Test
  public void testMethodHavingInvalidTypeReferences() throws Exception {
    runTest(
        ImmutableList.of(),
        ImmutableList.of(
            transformer(ClassWithMethodMissingTypeParameters.class)
                .setGenericSignature(
                    MethodPredicate.onName("test"),
                    existing -> {
                      // Replace the generic type parameter T with R.
                      return existing.replace("<T:", "<R:");
                    })
                .transform()),
        ClassWithMethodMissingTypeParameters.class,
        SignatureEvaluationResult.INVALID_TYPE_VARIABLE_UNDEFINED);
  }

  @Test
  public void testIncorrectNumberOfSuperInterfaces() throws Exception {
    runTest(
        ImmutableList.of(),
        ImmutableList.of(
            transformer(ClassWithInvalidNumberOfSuperInterfaces.class)
                .setImplements(I.class)
                .transform()),
        ClassWithInvalidNumberOfSuperInterfaces.class,
        SignatureEvaluationResult.INVALID_INTERFACE_COUNT);
  }

  @Test
  public void testMissingArgument() throws Exception {
    runTest(
        ImmutableList.of(J.class),
        ImmutableList.of(
            transformer(ClassWithInvalidArgumentCount.class)
                .setGenericSignature(
                    existing -> {
                      // Replace the generic type argument <TT;> with nothing
                      return existing.replace("<TT;>", "");
                    })
                .transform()),
        ClassWithInvalidArgumentCount.class,
        SignatureEvaluationResult.VALID);
  }

  @Test
  public void testTooManyArguments() throws Exception {
    runTest(
        ImmutableList.of(J.class),
        ImmutableList.of(
            transformer(ClassWithInvalidArgumentCount.class)
                .setGenericSignature(
                    existing -> {
                      // Replace the generic type argument <TT;> with nothing
                      return existing.replace("<TT;>", "<TT;TT;>");
                    })
                .transform()),
        ClassWithInvalidArgumentCount.class,
        SignatureEvaluationResult.INVALID_APPLICATION_COUNT);
  }

  @Test
  public void testClassWithInvalidSuperType() throws Exception {
    runTest(
        ImmutableList.of(Base.class, OtherBase.class),
        ImmutableList.of(
            transformer(ClassWithInvalidSuperType.class)
                .setSuper(DescriptorUtils.javaTypeToDescriptor(OtherBase.class.getTypeName()))
                .transform()),
        ClassWithInvalidSuperType.class,
        SignatureEvaluationResult.INVALID_SUPER_TYPE);
  }

  private void runTest(
      List<Class<?>> classes,
      List<byte[]> transformations,
      Class<?> classToVerify,
      SignatureEvaluationResult expected)
      throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(classes)
                .addClassProgramData(transformations)
                .addLibraryFile(ToolHelper.getJava8RuntimeJar())
                .build(),
            factory -> {
              ProguardConfiguration.Builder builder =
                  ProguardConfiguration.builder(
                      factory, new Reporter(new TestDiagnosticMessagesImpl()));
              builder.addKeepAttributePatterns(ImmutableList.of(ProguardKeepAttributes.SIGNATURE));
              return builder.build();
            });
    GenericSignatureContextBuilder contextBuilder = GenericSignatureContextBuilder.create(appView);
    GenericSignatureCorrectnessHelper check =
        GenericSignatureCorrectnessHelper.createForInitialCheck(appView, contextBuilder);
    DexProgramClass clazz =
        appView
            .definitionFor(
                appView
                    .dexItemFactory()
                    .createType(DescriptorUtils.javaTypeToDescriptor(classToVerify.getTypeName())))
            .asProgramClass();
    assertNotNull(clazz);
    assertEquals(expected, check.evaluateSignaturesForClass(clazz));
  }

  public interface I {}

  public interface J<T> {
    <R extends Object & I & J<Integer>> R foo(T foo);
  }

  public static class Base<T> {}

  public static class CustomException extends Exception {}

  public static class Empty {}

  public static class ClassWithClassBound<T extends Base<T /* R */>> {}

  public static class ClassWithInterfaceBound<T extends I & J<T /* R */>> {}

  public abstract static class ClassWithMembersHavingInvalidTypeReference<T /* R */> {

    T t;

    public abstract T testReturn();

    public abstract void testParameter(T t);
  }

  public abstract static class ClassOverridingTypeArgument<T> {

    public abstract <T> T test();
  }

  public abstract static class ClassWithMethodMissingTypeParameters {

    public abstract <T /* R */> T test(T foo);
  }

  public abstract static class ClassWithInvalidNumberOfSuperInterfaces<T>
      implements I, J<T> /* I */ {}

  public abstract static class ClassWithInvalidArgumentCount<T>
      implements J<T> /* J and J<T,T> */ {}

  public static class OtherBase<T> {}

  public abstract static class ClassWithInvalidSuperType<T> extends Base<T> /* OtherBase<T> */ {}
}
