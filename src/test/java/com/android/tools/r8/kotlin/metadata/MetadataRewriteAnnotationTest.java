// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmTypeAliasSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;
import kotlinx.metadata.KmAnnotationArgument.ArrayValue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteAnnotationTest extends KotlinMetadataTestBase {

  private final String EXPECTED =
      StringUtils.lines(
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Bar",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "UP",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "LEFT",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "RIGHT",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "DOWN",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "UP",
          "Top most",
          "class com.android.tools.r8.kotlin.metadata.annotation_lib.Foo",
          "DOWN",
          "com.android.tools.r8.kotlin.metadata.annotation_lib.Foo");
  private static final String PKG_LIB = PKG + ".annotation_lib";
  private static final String PKG_APP = PKG + ".annotation_app";
  private static final String FOO_ORIGINAL_NAME = PKG_LIB + ".Foo";
  private static final String FOO_FINAL_NAME = "a.b.c";

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteAnnotationTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> libJars = new HashMap<>();
  private final TestParameters parameters;

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"))
              .compile();
      libJars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.get(targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.get(targetVersion))
            /// Keep the annotations
            .addKeepClassAndMembersRules(PKG_LIB + ".AnnoWithClassAndEnum")
            .addKeepClassAndMembersRules(PKG_LIB + ".AnnoWithClassArr")
            .addKeepRules("-keep class " + PKG_LIB + ".Nested { *** kept(); } ")
            .addKeepRules("-keep class " + PKG_LIB + ".Nested { *** message(); } ")
            // .addKeepRules("-keep class " + PKG_LIB + ".Nested { *** kept; *** getKept(); } ")
            // Keep Foo but rename to test arguments
            .addKeepClassAndMembersRulesWithAllowObfuscation(FOO_ORIGINAL_NAME)
            .addApplyMapping(FOO_ORIGINAL_NAME + " -> " + FOO_FINAL_NAME + ":")
            // Keep Direction but rename the enum
            .addKeepClassAndMembersRules(PKG_LIB + ".Direction")
            // Keep Bar and Baz and Quux because we are directly reflecting on them
            .addKeepClassAndMembersRules(PKG_LIB + ".Bar")
            .addKeepClassAndMembersRules(PKG_LIB + ".Baz")
            .addKeepClassAndMembersRules(PKG_LIB + ".Quux")
            // Keep the static class for the type alias
            .addKeepClassAndMembersRules(PKG_LIB + ".LibKt")
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS,
                ProguardKeepAttributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS)
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addProgramFiles(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED.replace(FOO_ORIGINAL_NAME, FOO_FINAL_NAME));
  }

  private void inspect(CodeInspector inspector) {
    // Assert that foo is renamed.
    ClassSubject foo = inspector.clazz(PKG_LIB + ".Foo");
    assertThat(foo, isPresentAndRenamed());
    assertEquals(FOO_FINAL_NAME, foo.getFinalName());
    // Assert that bar exists and is not renamed.
    ClassSubject bar = inspector.clazz(PKG_LIB + ".Bar");
    assertThat(bar, isPresentAndNotRenamed());
    // Check that the annotation type on the type alias has been renamed
    inspectTypeAliasAnnotation(inspector, foo, bar);
  }

  private void inspectTypeAliasAnnotation(
      CodeInspector inspector, ClassSubject foo, ClassSubject bar) {
    ClassSubject libKt = inspector.clazz(PKG_LIB + ".LibKt");
    assertThat(libKt, isPresent());
    assertThat(libKt.getKmPackage(), isPresent());
    KmTypeAliasSubject qux = libKt.getKmPackage().kmTypeAliasWithUniqueName("Qux");
    assertThat(qux, isPresent());
    assertEquals(1, qux.annotations().size());
    KmAnnotation annotation = qux.annotations().get(0);
    assertEquals(
        DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB) + "/AnnoWithClassArr",
        annotation.getClassName());
    Map<String, KmAnnotationArgument<?>> arguments = annotation.getArguments();
    assertEquals(1, arguments.size());
    ArrayValue classes = (ArrayValue) arguments.get("classes");
    assertEquals(
        "KClassValue(value=" + foo.getFinalBinaryName() + ")",
        classes.getValue().get(0).toString());
    assertEquals(
        "KClassValue(value=" + bar.getFinalBinaryName() + ")",
        classes.getValue().get(1).toString());
  }
}
