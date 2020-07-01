// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
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

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInMultifileClassTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines(", 1, 2, 3");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInMultifileClassTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> multifileLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String multifileLibFolder = PKG_PREFIX + "/multifileclass_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path multifileLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(multifileLibFolder, "signed"),
                  getKotlinFileInTest(multifileLibFolder, "unsigned"))
              .compile();
      multifileLibJarMap.put(targetVersion, multifileLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = multifileLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".multifileclass_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInMultifileClass_merged() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(multifileLibJarMap.get(targetVersion))
            // Keep UtilKt#comma*Join*(). Let R8 optimize (inline) others, such as joinOf*(String).
            .addKeepRules("-keep class **.UtilKt")
            .addKeepRules("-keepclassmembers class * { ** comma*Join*(...); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectMerged)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/151193860): update to just .compile() once fixed.
            .compileRaw();
    // TODO(b/151193860): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(kotlinTestCompileResult.stderr, containsString("unresolved reference: join"));
  }

  private void inspectMerged(CodeInspector inspector) {
    String utilClassName = PKG + ".multifileclass_lib.UtilKt";

    ClassSubject util = inspector.clazz(utilClassName);
    assertThat(util, isPresentAndNotRenamed());
    MethodSubject commaJoinOfInt = util.uniqueMethodWithName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = util.uniqueMethodWithName("joinOfInt");
    assertThat(joinOfInt, not(isPresent()));

    inspectMetadataForFacade(inspector, util);
    // TODO(b/156290332): Seems like this test is incorrect and should never work.
    // inspectSignedKt(inspector);
  }

  @Test
  public void testMetadataInMultifileClass_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(multifileLibJarMap.get(targetVersion))
            // Keep UtilKt#comma*Join*().
            .addKeepRules("-keep class **.UtilKt")
            .addKeepRules("-keep,allowobfuscation class **.UtilKt__SignedKt")
            .addKeepRules("-keepclassmembers class * { ** comma*Join*(...); }")
            // Keep yet rename joinOf*(String).
            .addKeepRules("-keepclassmembers,allowobfuscation class * { ** joinOf*(...); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/multifileclass_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/151193860): update to just .compile() once fixed.
            .compileRaw();
    // TODO(b/151193860): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(kotlinTestCompileResult.stderr, containsString("unresolved reference: join"));
  }

  private void inspectRenamed(CodeInspector inspector) {
    String utilClassName = PKG + ".multifileclass_lib.UtilKt";

    ClassSubject util = inspector.clazz(utilClassName);
    assertThat(util, isPresentAndNotRenamed());
    MethodSubject commaJoinOfInt = util.uniqueMethodWithName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = util.uniqueMethodWithName("joinOfInt");
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
    MethodSubject commaJoinOfInt = signed.uniqueMethodWithName("commaSeparatedJoinOfInt");
    assertThat(commaJoinOfInt, isPresentAndNotRenamed());
    MethodSubject joinOfInt = signed.uniqueMethodWithName("joinOfInt");
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
