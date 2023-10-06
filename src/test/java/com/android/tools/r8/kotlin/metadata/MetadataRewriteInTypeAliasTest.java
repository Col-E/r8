// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isDexClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassifierSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeAliasSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInTypeAliasTest extends KotlinMetadataTestBase {
  private static final String EXPECTED =
      StringUtils.lines(
          "Impl::foo",
          "Program::foo",
          "true",
          "42",
          "42",
          "42",
          "42",
          "42",
          "42",
          "42",
          "true",
          "42",
          "1",
          "ClassWithCompanion::fooOnCompanion",
          "42",
          "42",
          "1",
          "Hello World!",
          "class com.android.tools.r8.kotlin.metadata.typealias_lib.Super");

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

  public MetadataRewriteInTypeAliasTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer typeAliasLibJarMap =
      getCompileMemoizer(
          getKotlinFileInTest(PKG_PREFIX + "/typealias_lib", "lib"),
          getKotlinFileInTest(PKG_PREFIX + "/typealias_lib", "lib_ext"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = typeAliasLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typealias_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".typealias_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInTypeAlias_renamed() throws Exception {
    String superTypeName = "com.android.tools.r8.kotlin.metadata.typealias_lib.Super";
    String renamedSuperTypeName = "com.android.tools.r8.kotlin.metadata.typealias_lib.FooBar";
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(typeAliasLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep non-private members of Impl
            .addKeepRules("-keep class **.Impl { !private *; }")
            // Keep but allow obfuscation of types.
            .addKeepRules("-keep,allowobfuscation class " + PKG + ".typealias_lib.** { *; }")
            .addKeepRules("-keepclassmembernames class " + PKG + ".typealias_lib.**" + " { *; }")
            // Keep the Companion class for ClassWithCompanionC.
            .addKeepRules("-keep class **.ClassWithCompanion$Companion { *; }")
            // Keep the inner class, otherwise it cannot be constructed.
            .addKeepRules("-keep class **.*Inner { *; }")
            // Keep LibKt that contains the type-aliases and utils.
            .addKeepRules("-keep class **.LibKt, **.Lib_extKt { *; }")
            // Keep the library test methods
            .addKeepRules("-keep class " + PKG + ".typealias_lib.*Tester { *; }")
            .addKeepRules("-keep class " + PKG + ".typealias_lib.*Tester$Companion { *; }")
            .addKeepRules("-keep class " + PKG + ".typealias_lib.SubTypeOfAlias { *; }")
            .addApplyMapping(superTypeName + " -> " + renamedSuperTypeName + ":")
            .addKeepKotlinMetadata()
            .addKeepAttributes(
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path appJar =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typealias_app", "main"))
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(appJar)
        .run(parameters.getRuntime(), PKG + ".typealias_app.MainKt")
        .assertSuccessWithOutput(EXPECTED.replace(superTypeName, renamedSuperTypeName));
  }

  private void inspect(CodeInspector inspector) {
    inspectLib(inspector);
    inspectLibExt(inspector);
  }

  private void inspectLib(CodeInspector inspector) {
    String packageName = PKG + ".typealias_lib";
    String itfClassName = packageName + ".Itf";
    String libKtClassName = packageName + ".LibKt";

    ClassSubject itf = inspector.clazz(itfClassName);
    assertThat(itf, isPresentAndRenamed());

    ClassSubject libKt = inspector.clazz(libKtClassName);
    assertThat(libKt, isPresentAndNotRenamed());

    MethodSubject seq = libKt.uniqueMethodWithOriginalName("seq");
    assertThat(seq, isPresentAndNotRenamed());

    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    String arrayDescriptor =
        DescriptorUtils.getDescriptorFromKotlinClassifier(ClassClassifiers.arrayBinaryName);

    // Check that typealias myAliasedArray<T> = Array<T> exists.
    KmTypeAliasSubject myAliasedArray = kmPackage.kmTypeAliasWithUniqueName("myAliasedArray");
    assertThat(myAliasedArray, isPresent());
    assertEquals(arrayDescriptor, myAliasedArray.expandedType().descriptor());

    // Check that typealias API = Itf has been rewritten correctly.
    KmTypeAliasSubject api = kmPackage.kmTypeAliasWithUniqueName("API");
    assertThat(api, isPresent());
    assertThat(api.expandedType(), isDexClass(itf.getDexProgramClass()));
    assertThat(api.underlyingType(), isDexClass(itf.getDexProgramClass()));

    // Check that the type-alias APIs exist and that the expanded type is renamed.
    KmTypeAliasSubject apIs = kmPackage.kmTypeAliasWithUniqueName("APIs");
    assertThat(apIs, isPresent());
    assertEquals(arrayDescriptor, apIs.expandedType().descriptor());
    assertEquals(1, apIs.expandedType().typeArguments().size());
    KmTypeProjectionSubject expandedArgument = apIs.expandedType().typeArguments().get(0);
    assertThat(expandedArgument.type(), isDexClass(itf.getDexProgramClass()));

    Assume.assumeFalse("TODO(b/303374432)", kotlinParameters.isKotlinDev());
    assertEquals(myAliasedArray.descriptor(packageName), apIs.underlyingType().descriptor());
    assertEquals(1, apIs.underlyingType().typeArguments().size());
    KmTypeProjectionSubject underlyingArgument = apIs.underlyingType().typeArguments().get(0);
    KmTypeSubject type = underlyingArgument.type();
    assertNotNull(type);
    assertTrue(type.classifier().isTypeAlias());
    assertEquals(api.descriptor(packageName), type.descriptor());
  }

  private void inspectLibExt(CodeInspector inspector) {
    String packageName = PKG + ".typealias_lib";
    String libKtClassName = packageName + ".Lib_extKt";

    // Check that Arr has been renamed.
    ClassSubject arr = inspector.clazz(packageName + ".Arr");
    assertThat(arr, isPresentAndRenamed());

    ClassSubject libKt = inspector.clazz(libKtClassName);
    KmPackageSubject kmPackage = libKt.getKmPackage();

    // typealias Arr1D<K> = Arr<K>
    KmTypeAliasSubject arr1D = kmPackage.kmTypeAliasWithUniqueName("Arr1D");
    assertThat(arr1D, isPresent());
    assertThat(arr1D.expandedType(), isDexClass(arr.getDexProgramClass()));

    // typealias Arr2D<K> = Arr1D<Arr1D<K>>
    KmTypeAliasSubject arr2D = kmPackage.kmTypeAliasWithUniqueName("Arr2D");
    assertThat(arr2D, isPresent());
    assertThat(arr2D.expandedType(), isDexClass(arr.getDexProgramClass()));
    assertEquals(1, arr2D.expandedType().typeArguments().size());
    KmTypeProjectionSubject arr2DexpandedArg = arr2D.expandedType().typeArguments().get(0);
    assertThat(arr2DexpandedArg.type(), isDexClass(arr.getDexProgramClass()));

    assertEquals(arr1D.descriptor(packageName), arr2D.underlyingType().descriptor());
    assertEquals(1, arr2D.underlyingType().typeArguments().size());
    KmTypeProjectionSubject arr2DunderlyingArg = arr2D.underlyingType().typeArguments().get(0);
    assertEquals(arr1D.descriptor(packageName), arr2DunderlyingArg.type().descriptor());

    // typealias IntSet = Set<Int>
    // typealias MyMapToSetOfInt<K> = MutableMap<K, IntSet>
    KmTypeAliasSubject intSet = kmPackage.kmTypeAliasWithUniqueName("IntSet");
    assertThat(intSet, isPresent());

    KmTypeAliasSubject myMapToSetOfInt = kmPackage.kmTypeAliasWithUniqueName("MyMapToSetOfInt");
    assertThat(myMapToSetOfInt, isPresent());
    assertEquals(2, myMapToSetOfInt.underlyingType().typeArguments().size());
    assertEquals(2, myMapToSetOfInt.expandedType().typeArguments().size());
    assertEquals(1, myMapToSetOfInt.typeParameters().size());
    KmClassifierSubject typeClassifier =
        myMapToSetOfInt.underlyingType().typeArguments().get(0).type().classifier();
    assertTrue(typeClassifier.isTypeParameter());
    // Check that the type-variable K in 'MyMapToSetOfInt<K>' is the first argument in
    // MutableMap<K, IntSet>.
    assertEquals(
        myMapToSetOfInt.typeParameters().get(0).getId(), typeClassifier.asTypeParameter().getId());

    KmTypeSubject underlyingType = myMapToSetOfInt.underlyingType().typeArguments().get(1).type();
    assertEquals(intSet.descriptor(packageName), underlyingType.descriptor());

    KmTypeSubject expandedType = myMapToSetOfInt.expandedType().typeArguments().get(1).type();
    assertTrue(intSet.expandedType().equalUpToAbbreviatedType(expandedType));

    // Check that the following exist:
    // typealias MyHandler = (Int, Any) -> Unit
    // typealias MyGenericPredicate<T> = (T) -> Boolean
    assertThat(kmPackage.kmTypeAliasWithUniqueName("MyHandler"), isPresent());
    KmTypeAliasSubject genericPredicate = kmPackage.kmTypeAliasWithUniqueName("MyGenericPredicate");
    assertThat(genericPredicate, isPresent());

    // Check that the type-variable T in 'MyGenericPredicate<T>' is the input argument in
    // (T) -> Boolean.
    assertEquals(1, genericPredicate.typeParameters().size());
    assertEquals(2, genericPredicate.expandedType().typeArguments().size());
    KmTypeProjectionSubject kmTypeGenericArgumentSubject =
        genericPredicate.expandedType().typeArguments().get(0);
    assertTrue(kmTypeGenericArgumentSubject.type().classifier().isTypeParameter());
    assertEquals(
        genericPredicate.typeParameters().get(0).getId(),
        kmTypeGenericArgumentSubject.type().classifier().asTypeParameter().getId());

    // typealias ClassWithCompanionC = ClassWithCompanion.Companion
    KmTypeAliasSubject classWithCompanionC =
        kmPackage.kmTypeAliasWithUniqueName("ClassWithCompanionC");
    assertThat(classWithCompanionC, isPresent());

    ClassSubject companionClazz = inspector.clazz(packageName + ".ClassWithCompanion$Companion");
    assertThat(classWithCompanionC.expandedType(), isDexClass(companionClazz.getDexProgramClass()));
  }
}
