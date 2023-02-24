// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isDexClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import com.android.tools.r8.utils.codeinspector.KmTypeParameterSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeParameterSubjectMixin;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.KmValueParameterSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import kotlinx.metadata.KmClassifier.TypeParameter;
import kotlinx.metadata.KmVariance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInTypeArgumentsTest extends KotlinMetadataTestBase {

  private static final String LIB_PKG = PKG + ".typeargument_lib.";

  private static final int FLAG_NONE = 0;
  private static final int FLAG_REIFIED = 1;

  private static final String EXPECTED =
      StringUtils.lines(
          "Hello World!",
          "42",
          "1",
          "42",
          "42",
          "1",
          "42",
          "42",
          "42",
          "1",
          "42",
          "1",
          "42",
          "42",
          "42",
          "42",
          "42",
          "1",
          "2",
          "9",
          "3",
          "7",
          "9",
          "42",
          "42",
          "7");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInTypeArgumentsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer jarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/typeargument_lib", "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = jarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typeargument_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".typeargument_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInTypeAliasWithR8() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep ClassThatWillBeObfuscated, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **ClassThatWillBeObfuscated")
            .addKeepRules("-keepclassmembers class **ClassThatWillBeObfuscated { *; }")
            // Keep all other classes.
            .addKeepRules("-keep class **typeargument_lib.PlainBox { *; }")
            .addKeepRules("-keep class **typeargument_lib.SomeClass { *; }")
            .addKeepRules("-keep class **typeargument_lib.CoVariant { *; }")
            .addKeepRules("-keep class **typeargument_lib.ContraVariant { *; }")
            .addKeepRules("-keep class **typeargument_lib.Invariant { *; }")
            .addKeepRules("-keep class **typeargument_lib.LibKt { *; }")
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    Path mainJar =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typeargument_app", "main"))
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(mainJar)
        .run(parameters.getRuntime(), PKG + ".typeargument_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    inspectInvariant(inspector);
    inspectCoVariant(inspector);
    inspectContraVariant(inspector);
    inspectExtensions(inspector);
  }

  private void inspectInvariant(CodeInspector inspector) {
    ClassSubject someClass = inspector.clazz(LIB_PKG + "SomeClass");
    assertThat(someClass, isPresent());
    ClassSubject classThatShouldBeObfuscated =
        inspector.clazz(LIB_PKG + "ClassThatWillBeObfuscated");
    assertThat(classThatShouldBeObfuscated, isPresentAndRenamed());

    // Check that the type-parameters of Invariant is marked as INVARIANT.
    ClassSubject invariant = inspector.clazz(LIB_PKG + "Invariant");
    assertThat(invariant, isPresent());
    KmClassSubject kmClass = invariant.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(2, kmClass.typeParameters().size());
    inspectTypeParameter(kmClass, "T", 0, FLAG_NONE, KmVariance.INVARIANT);
    inspectTypeParameter(kmClass, "C", 1, FLAG_NONE, KmVariance.INVARIANT);

    // Check that funGenerics method has a type-parameter with id = 2.
    KmFunctionSubject funGenerics = kmClass.kmFunctionWithUniqueName("funGenerics");
    assertThat(funGenerics, isPresent());
    assertEquals(1, funGenerics.typeParameters().size());
    inspectTypeParameter(funGenerics, "R", 2, FLAG_NONE, KmVariance.INVARIANT);
    assertEquals(1, funGenerics.valueParameters().size());
    KmValueParameterSubject kmValueParameterSubject = funGenerics.valueParameters().get(0);
    assertTrue(kmValueParameterSubject.type().classifier().isTypeParameter());
    TypeParameter typeParameter = kmValueParameterSubject.type().classifier().asTypeParameter();
    assertEquals(2, typeParameter.getId());

    // Check that the funGenerics method return type is referencing the method type parameter.
    KmTypeSubject funGenericsReturnType = funGenerics.returnType();
    assertTrue(funGenericsReturnType.classifier().isTypeParameter());
    assertEquals(2, funGenericsReturnType.classifier().asTypeParameter().getId());

    // Check funGenericsWithUpperBounds has an upperBound of SomeClass.
    KmFunctionSubject funGenericsWithUpperBounds =
        kmClass.kmFunctionWithUniqueName("funGenericsWithUpperBounds");
    assertThat(funGenericsWithUpperBounds, isPresent());
    assertEquals(1, funGenericsWithUpperBounds.typeParameters().size());
    inspectTypeParameter(funGenericsWithUpperBounds, "R", 2, FLAG_NONE, KmVariance.INVARIANT);
    KmTypeParameterSubject methodTypeParameter = funGenericsWithUpperBounds.typeParameters().get(0);
    List<KmTypeSubject> upperBounds = methodTypeParameter.upperBounds();
    assertEquals(2, upperBounds.size());
    assertThat(upperBounds.get(0), isDexClass(someClass.getDexProgramClass()));
    assertEquals(KT_COMPARABLE, upperBounds.get(1).descriptor());
    // Check that the upper bound has a type argument.
    assertEquals(1, upperBounds.get(1).typeArguments().size());
    assertThat(
        upperBounds.get(1).typeArguments().get(0).type(),
        isDexClass(someClass.getDexProgramClass()));
  }

  private void inspectCoVariant(CodeInspector inspector) {
    // Check that the type-parameter for CoVariant is marked as OUT.
    ClassSubject invariant = inspector.clazz(LIB_PKG + "CoVariant");
    assertThat(invariant, isPresent());
    KmClassSubject kmClass = invariant.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(1, kmClass.typeParameters().size());
    inspectTypeParameter(kmClass, "T", 0, FLAG_NONE, KmVariance.OUT);
    // Check that the return type of the property CoVariant.t refers to the type parameter.
    assertEquals(1, kmClass.getProperties().size());
    KmPropertySubject t = kmClass.kmPropertyWithUniqueName("t");
    assertThat(t, isPresent());
    assertTrue(t.returnType().typeArguments().isEmpty());
    assertEquals(
        kmClass.typeParameters().get(0).getId(),
        t.returnType().classifier().asTypeParameter().getId());
  }

  private void inspectContraVariant(CodeInspector inspector) {
    // Check that the type-parameter for ContraVariant is marked as IN.
    ClassSubject invariant = inspector.clazz(LIB_PKG + "ContraVariant");
    assertThat(invariant, isPresent());
    KmClassSubject kmClass = invariant.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(1, kmClass.typeParameters().size());
    inspectTypeParameter(kmClass, "T", 0, FLAG_NONE, KmVariance.IN);
  }

  private void inspectExtensions(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(LIB_PKG + "LibKt");
    assertThat(clazz, isPresent());
    KmPackageSubject kmPackage = clazz.getKmPackage();
    assertThat(kmPackage, isPresent());
    KmFunctionSubject asListWithVarargs =
        kmPackage.kmFunctionExtensionWithUniqueName("asListWithVarargs");
    assertThat(asListWithVarargs, isExtensionFunction());
    inspectTypeParameter(asListWithVarargs, "T", 0, FLAG_REIFIED, KmVariance.INVARIANT);
    // Check that the varargs type argument has OUT invariance.
    List<KmTypeProjectionSubject> kmTypeProjectionSubjects =
        asListWithVarargs.returnType().typeArguments();
    assertEquals(1, kmTypeProjectionSubjects.size());
    KmTypeSubject type = kmTypeProjectionSubjects.get(0).type();
    assertEquals(1, type.typeArguments().size());
    KmTypeProjectionSubject kmTypeProjectionSubject = type.typeArguments().get(0);
    assertEquals(KmVariance.OUT, kmTypeProjectionSubject.variance());
  }

  private void inspectTypeParameter(
      KmTypeParameterSubjectMixin subject, String name, int id, int flags, KmVariance variance) {
    KmTypeParameterSubject typeParameter = subject.kmTypeParameterWithUniqueName(name);
    assertThat(typeParameter, isPresent());
    assertEquals(id, typeParameter.getId());
    assertEquals(flags, typeParameter.getFlags());
    assertEquals(variance, typeParameter.getVariance());
  }
}
