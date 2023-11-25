// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInSealedClassTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("6");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteInSealedClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer sealedLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/sealed_lib", "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = sealedLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/sealed_app", "valid"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".sealed_app.ValidKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInSealedClass_valid() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(sealedLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the Expr class
            .addKeepRules("-keep class **.Expr")
            // Keep the extension function
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            // Keep the factory object and utils
            .addKeepRules("-keep class **.ExprFactory { *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectValid)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/sealed_app", "valid"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".sealed_app.ValidKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectValid(CodeInspector inspector) {
    String numClassName = PKG + ".sealed_lib.Num";
    String exprClassName = PKG + ".sealed_lib.Expr";
    String libClassName = PKG + ".sealed_lib.LibKt";

    ClassSubject num = inspector.clazz(numClassName);
    assertThat(num, isPresentAndRenamed());

    ClassSubject expr = inspector.clazz(exprClassName);
    assertThat(expr, isPresentAndNotRenamed());

    KmClassSubject kmClass = expr.getKmClass();
    assertThat(kmClass, isPresent());

    assertFalse(kmClass.getSealedSubclassDescriptors().isEmpty());
    kmClass
        .getSealedSubclassDescriptors()
        .forEach(
            sealedSubclassDescriptor -> {
              ClassSubject sealedSubclass =
                  inspector.clazz(descriptorToJavaType(sealedSubclassDescriptor));
              assertThat(sealedSubclass, isPresentAndRenamed());
              assertEquals(sealedSubclassDescriptor, sealedSubclass.getFinalDescriptor());
            });

    ClassSubject libKt = inspector.clazz(libClassName);
    assertThat(expr, isPresentAndNotRenamed());

    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject eval = kmPackage.kmFunctionExtensionWithUniqueName("eval");
    assertThat(eval, isPresent());
    assertThat(eval, isExtensionFunction());
  }

  @Test
  public void testMetadataInSealedClass_invalid() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(sealedLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the Expr class
            .addKeepRules("-keep class **.Expr")
            // Keep the extension function
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectInvalid)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFilesWithNonKtExtension(
                temp, getFileInTest(PKG_PREFIX + "/sealed_app", "invalid.kt_txt"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();

    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    if (kotlinParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_5_0)) {
      assertThat(kotlinTestCompileResult.stderr, containsString(sealedClassErrorMessage()));
    } else {
      assertThat(kotlinTestCompileResult.stderr, containsString("cannot access"));
      assertThat(kotlinTestCompileResult.stderr, containsString("private in 'Expr'"));
    }
  }

  private String sealedClassErrorMessage() {
    if (kotlinParameters.isKotlinDev()) {
      return "a class can only extend a sealed class or interface declared in the same package";
    }
    return "inheritance of sealed classes or interfaces from different module is prohibited";
  }

  private void inspectInvalid(CodeInspector inspector) {
    String exprClassName = PKG + ".sealed_lib.Expr";
    String libClassName = PKG + ".sealed_lib.LibKt";

    ClassSubject expr = inspector.clazz(exprClassName);
    assertThat(expr, isPresentAndNotRenamed());

    KmClassSubject kmClass = expr.getKmClass();
    assertThat(kmClass, isPresent());

    ClassSubject libKt = inspector.clazz(libClassName);
    assertThat(expr, isPresentAndNotRenamed());

    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject eval = kmPackage.kmFunctionExtensionWithUniqueName("eval");
    assertThat(eval, isPresent());
    assertThat(eval, isExtensionFunction());
  }
}
