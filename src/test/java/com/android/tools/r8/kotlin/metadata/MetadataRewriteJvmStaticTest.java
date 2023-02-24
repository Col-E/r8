// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.metadata.jvmstatic_app.MainJava;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteJvmStaticTest extends KotlinMetadataTestBase {

  private static final String EXPECTED =
      StringUtils.lines("Hello, Hello", "Calling func...", "Foo");
  private static final String PKG_LIB = PKG + ".jvmstatic_lib";
  private static final String PKG_APP = PKG + ".jvmstatic_app";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> data() {
    // We are testing static methods on interfaces which requires java 8.
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build());
  }

  public MetadataRewriteJvmStaticTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static KotlinCompileMemoizer kotlincLibJar =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void smokeTestJava() throws Exception {
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
        .addProgramClassFileData(MainJava.dump())
        .run(parameters.getRuntime(), MainJava.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadata() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepAllClassesRule()
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    testKotlin(libJar);
    testJava(libJar);
  }

  private void testKotlin(Path libJar) throws Exception {
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void testJava(Path libJar) throws Exception {
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addProgramClassFileData(MainJava.dump())
        .run(parameters.getRuntime(), MainJava.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    inspectLib(inspector);
    inspectInterfaceWithCompanion(inspector);
  }

  private void inspectLib(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(PKG_LIB + ".Lib");
    assertThat(clazz, isPresent());
    KmClassSubject kmClass = clazz.getKmClass();
    assertThat(kmClass, isPresent());
    KmFunctionSubject staticFun = kmClass.kmFunctionWithUniqueName("staticFun");
    assertThat(staticFun, isPresent());
    assertEquals("staticFun(Lkotlin/jvm/functions/Function0;)V", staticFun.signature().asString());
    KmPropertySubject staticProp = kmClass.kmPropertyWithUniqueName("staticProp");
    assertThat(staticProp, isPresent());
  }

  private void inspectInterfaceWithCompanion(CodeInspector inspector) {
    ClassSubject itf = inspector.clazz(PKG_LIB + ".InterfaceWithCompanion");
    assertThat(itf, isPresent());
    MethodSubject greet = itf.uniqueMethodWithOriginalName("greet");
    assertThat(greet, isPresent());

    ClassSubject itfCompanion = inspector.clazz(PKG_LIB + ".InterfaceWithCompanion$Companion");
    assertThat(itfCompanion, isPresent());
    KmClassSubject kmClass = itfCompanion.getKmClass();
    KmFunctionSubject greetKm = kmClass.kmFunctionWithUniqueName("greet");
    assertThat(greetKm, isPresent());
    assertEquals("greet(Ljava/lang/String;)V", greetKm.signature().asString());
  }
}
