// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInSealedClassTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInSealedClassTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> sealedLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String sealedLibFolder = PKG_PREFIX + "/sealed_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path sealedLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(sealedLibFolder, "lib"))
              .compile();
      sealedLibJarMap.put(targetVersion, sealedLibJar);
    }
  }

  @Test
  public void testMetadataInSealedClass_valid() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(sealedLibJarMap.get(targetVersion))
            // Keep the Expr class
            .addKeepRules("-keep class **.Expr")
            // Keep the extension function
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            // Keep the factory object and utils
            .addKeepRules("-keep class **.ExprFactory { *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String numClassName = pkg + ".sealed_lib.Num";
    final String exprClassName = pkg + ".sealed_lib.Expr";
    final String libClassName = pkg + ".sealed_lib.LibKt";
    compileResult.inspect(inspector -> {
      ClassSubject num = inspector.clazz(numClassName);
      assertThat(num, isRenamed());

      ClassSubject expr = inspector.clazz(exprClassName);
      assertThat(expr, isPresent());
      assertThat(expr, not(isRenamed()));

      KmClassSubject kmClass = expr.getKmClass();
      assertThat(kmClass, isPresent());

      kmClass.getSealedSubclassDescriptors().forEach(sealedSubclassDescriptor -> {
        ClassSubject sealedSubclass =
            inspector.clazz(descriptorToJavaType(sealedSubclassDescriptor));
        assertThat(sealedSubclass, isRenamed());
        assertEquals(sealedSubclassDescriptor, sealedSubclass.getFinalDescriptor());
      });

      ClassSubject libKt = inspector.clazz(libClassName);
      assertThat(expr, isPresent());
      assertThat(expr, not(isRenamed()));

      KmPackageSubject kmPackage = libKt.getKmPackage();
      assertThat(kmPackage, isPresent());

      KmFunctionSubject eval = kmPackage.kmFunctionExtensionWithUniqueName("eval");
      assertThat(eval, isPresent());
      assertThat(eval, isExtensionFunction());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/sealed_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "valid"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".sealed_app.ValidKt")
        .assertSuccessWithOutputLines("6");
  }

  @Test
  public void testMetadataInSealedClass_invalid() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(sealedLibJarMap.get(targetVersion))
            // Keep the Expr class
            .addKeepRules("-keep class **.Expr")
            // Keep the extension function
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String exprClassName = pkg + ".sealed_lib.Expr";
    final String numClassName = pkg + ".sealed_lib.Num";
    final String libClassName = pkg + ".sealed_lib.LibKt";
    compileResult.inspect(inspector -> {
      // Without any specific keep rule and no instantiation point, it's not necessary to keep
      // sub classes of Expr.
      ClassSubject num = inspector.clazz(numClassName);
      assertThat(num, not(isPresent()));

      ClassSubject expr = inspector.clazz(exprClassName);
      assertThat(expr, isPresent());
      assertThat(expr, not(isRenamed()));

      KmClassSubject kmClass = expr.getKmClass();
      assertThat(kmClass, isPresent());

      assertTrue(kmClass.getSealedSubclassDescriptors().isEmpty());

      ClassSubject libKt = inspector.clazz(libClassName);
      assertThat(expr, isPresent());
      assertThat(expr, not(isRenamed()));

      KmPackageSubject kmPackage = libKt.getKmPackage();
      assertThat(kmPackage, isPresent());

      KmFunctionSubject eval = kmPackage.kmFunctionExtensionWithUniqueName("eval");
      assertThat(eval, isPresent());
      assertThat(eval, isExtensionFunction());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/sealed_app";
    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "invalid"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();

    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(kotlinTestCompileResult.stderr, containsString("cannot access"));
    assertThat(kotlinTestCompileResult.stderr, containsString("private in 'Expr'"));
  }
}
