// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteAllowAccessModificationTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".allow_access_modification_lib";
  private static final String PKG_APP = PKG + ".allow_access_modification_app";
  private final String EXPECTED =
      StringUtils.lines(
          "4",
          "2",
          "42",
          "3",
          "1",
          "42",
          "5",
          "6",
          "7",
          "funPrivate",
          "funInternal",
          "funProtected",
          "extensionPrivate",
          "extensionInternal",
          "companionPrivate",
          "companionInternal",
          "staticPrivate",
          "staticInternal");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteAllowAccessModificationTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));
  private static final KotlinCompileMemoizer libReferenceJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib_reference"));
  private final TestParameters parameters;

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libReferenceJars.getForConfiguration(kotlinc, targetVersion);
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

  @Test
  public void testMetadataForLib() throws Exception {
    // We compile libjar with -allowaccesmodification such that all visibility modifiers are updated
    // to be public. We also compile it with a mapping file to rename Lib to LibReference. After
    // running with R8, the output should be binary compatible with libReference.
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepRules("-keepclassmembers,allowaccessmodification class **.Lib { *; }")
            .addKeepRules("-keep,allowaccessmodification,allowobfuscation class **.Lib { *; }")
            .addKeepRules("-keepclassmembers,allowaccessmodification class **.Lib$Comp { *; }")
            .addKeepRules("-keep,allowaccessmodification,allowobfuscation class **.Lib$Comp { *; }")
            .addKeepRules("-keep,allowaccessmodification,allowobfuscation class **.LibKt { *; }")
            .addApplyMapping(
                StringUtils.lines(
                    PKG_LIB + ".Lib -> " + PKG_LIB + ".LibReference:",
                    PKG_LIB + ".Lib$Comp -> " + PKG_LIB + ".LibReference$Comp:",
                    PKG_LIB + ".LibKt -> " + PKG_LIB + ".LibReferenceKt:",
                    "  void extensionPrivate(" + PKG_LIB + ".Lib) -> extensionPrivate",
                    "  void extensionInternal(" + PKG_LIB + ".Lib) -> extensionInternal",
                    "  void staticPrivate() -> staticPrivateReference",
                    "  void staticInternal() -> staticInternalReference"))
            .addKeepRuntimeVisibleAnnotations()
            .allowAccessModification()
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    ProcessResult mainResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .disableAssertions()
            .compileRaw();
    assertEquals(1, mainResult.exitCode);
    assertThat(
        mainResult.stderr, containsString(cannotAccessMessage(kotlinParameters, "LibReference")));
  }

  private void inspect(CodeInspector inspector) {
    // TODO(b/154348683): Assert equality between LibReference and Lib.
    // assertEqualMetadata(new CodeInspector(libReferenceJars.get(targetVersion)), inspector);
    ClassSubject lib = inspector.clazz(PKG_LIB + ".Lib");
    MethodSubject funInline = lib.uniqueMethodWithOriginalName("funInline$main");
    assertThat(funInline, isPresent());
    // TODO(b/154348683): Keep the inline method package private.
    // assertTrue(funInline.isPackagePrivate());
  }
}
