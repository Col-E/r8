// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInMultifileClassTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines(", 1, 2, 3", ", 1, 2, 3");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withOldCompilersStartingFrom(KotlinCompilerVersion.KOTLINC_1_4_20)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInMultifileClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer multifileLibJarMap =
      getCompileMemoizer(
          getKotlinFileInTest(PKG_PREFIX + "/multifileclass_lib", "signed"),
          getKotlinFileInTest(PKG_PREFIX + "/multifileclass_lib", "unsigned"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = multifileLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".multifileclass_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInMultifileClass_merged() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(multifileLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep UtilKt#comma*Join*(). Let R8 optimize (inline) others, such as joinOf*(String).
            .addKeepRules("-keep class **.UtilKt")
            .addKeepRules("-keepclassmembers class * { ** comma*Join*(...); }")
            .addKeepKotlinMetadata()
            .compile()
            .inspect(this::inspectMerged)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(
        kotlinTestCompileResult.stderr,
        containsString(unresolvedReferenceMessage(kotlinParameters, "join")));
  }

  private void inspectMerged(CodeInspector inspector) {
    String utilClassName = PKG + ".multifileclass_lib.UtilKt";

    ClassSubject util = inspector.clazz(utilClassName);
    assertThat(util, isPresentAndNotRenamed());
    MethodSubject commaJoinOfInt = util.uniqueMethodWithOriginalName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = util.uniqueMethodWithOriginalName("joinOfInt");
    assertThat(joinOfInt, not(isPresent()));

    inspectMetadataForFacade(inspector, util);
  }

  @Test
  public void testMetadataInMultifileClass_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(multifileLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep UtilKt#comma*Join*().
            .addKeepRules("-keep class **.UtilKt")
            .addKeepRules("-keep,allowobfuscation class **.UtilKt__SignedKt")
            .addKeepRules("-keep,allowobfuscation class **.UtilKt__UnsignedKt")
            .addKeepRules("-keepclassmembers class * { ** comma*Join*(...); }")
            // Keep yet rename joinOf*(String).
            .addKeepRules("-keepclassmembers,allowobfuscation class * { ** joinOf*(...); }")
            .addKeepKotlinMetadata()
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".multifileclass_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectRenamed(CodeInspector inspector) {
    String utilClassName = PKG + ".multifileclass_lib.UtilKt";

    ClassSubject util = inspector.clazz(utilClassName);
    assertThat(util, isPresentAndNotRenamed());
    MethodSubject commaJoinOfInt = util.uniqueMethodWithOriginalName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = util.uniqueMethodWithOriginalName("joinOfInt");
    assertThat(joinOfInt, isPresentAndRenamed());

    inspectMetadataForFacade(inspector, util);

    inspectSignedKt(inspector);
  }

  private void inspectMetadataForFacade(CodeInspector inspector, ClassSubject util) {
    // API entry is kept, hence the presence of Metadata.
    AnnotationSubject annotationSubject = util.annotation(METADATA_TYPE);
    assertThat(annotationSubject, isPresent());
    KotlinClassMetadata metadata = util.getKotlinClassMetadata();
    assertNotNull(metadata);
    assertTrue(metadata instanceof KotlinClassMetadata.MultiFileClassFacade);
    KotlinClassMetadata.MultiFileClassFacade facade =
        (KotlinClassMetadata.MultiFileClassFacade) metadata;
    List<String> partClassNames = facade.getPartClassNames();
    assertEquals(2, partClassNames.size());
    for (String partClassName : partClassNames) {
      ClassSubject partClass =
          inspector.clazz(DescriptorUtils.getJavaTypeFromBinaryName(partClassName));
      assertThat(partClass, isPresentAndRenamed());
    }
  }

  private void inspectSignedKt(CodeInspector inspector) {
    String signedClassName = PKG + ".multifileclass_lib.UtilKt__SignedKt";
    ClassSubject signed = inspector.clazz(signedClassName);
    assertThat(signed, isPresentAndRenamed());
    MethodSubject commaJoinOfInt = signed.uniqueMethodWithOriginalName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = signed.uniqueMethodWithOriginalName("joinOfInt");
    assertThat(joinOfInt, isPresentAndRenamed());

    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = signed.getKmPackage();
    assertThat(kmPackage, isPresent());
    KmFunctionSubject kmFunction =
        kmPackage.kmFunctionExtensionWithUniqueName("commaSeparatedJoinOfInt");
    assertThat(kmFunction, isPresent());
    assertThat(kmFunction, isExtensionFunction());
    // TODO(b/151193860): Inspect parameter type has a correct type argument, Int.
    // TODO(b/151193860): Inspect the name in KmFunction is still 'join' so that apps can refer.
  }
}
