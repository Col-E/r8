// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKotlinClassifier;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInRenamedTypeTest extends KotlinMetadataTestBase {
  private static final String OBFUSCATE_RENAMED = "-keep,allowobfuscation class **.Renamed { *; }";
  private static final String KEEP_KEPT = "-keep class **.Kept { *; }";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteInRenamedTypeTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer annoJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/anno", "Anno"));
  private static final KotlinCompileMemoizer inputJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/anno", "main"))
          .configure(
              kotlinCompilerTool -> {
                kotlinCompilerTool.addClasspathFiles(
                    annoJarMap.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });

  @Test
  public void testR8_kotlinStdlibAsLib() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(
            annoJarMap.getForConfiguration(kotlinc, targetVersion),
            ToolHelper.getJava8RuntimeJar(),
            kotlinc.getKotlinStdlibJar())
        .addProgramFiles(inputJarMap.getForConfiguration(kotlinc, targetVersion))
        .addKeepRules(OBFUSCATE_RENAMED, KEEP_KEPT)
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8_kotlinStdlibAsClassPath() throws Exception {
    testForR8(parameters.getBackend())
        .addClasspathFiles(
            annoJarMap.getForConfiguration(kotlinc, targetVersion), kotlinc.getKotlinStdlibJar())
        .addProgramFiles(inputJarMap.getForConfiguration(kotlinc, targetVersion))
        .addKeepRules(OBFUSCATE_RENAMED, KEEP_KEPT)
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8_kotlinStdlibAsProgramFile() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(
            annoJarMap.getForConfiguration(kotlinc, targetVersion), kotlinc.getKotlinStdlibJar())
        .addProgramFiles(inputJarMap.getForConfiguration(kotlinc, targetVersion))
        .addKeepRules(OBFUSCATE_RENAMED, KEEP_KEPT)
        .addKeepRules("-keep class **.Anno")
        .addKeepKotlinMetadata()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertWarningMessageThatMatches(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        // TODO(b/155536535): Enable this assert.
        // .assertInfoMessageThatMatches(expectedInfoMessagesFromKotlinStdLib())
        .assertNoErrorMessages()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    String pkg = getClass().getPackage().getName();
    ClassSubject kept = inspector.clazz(pkg + ".anno.Kept");
    assertThat(kept, isPresentAndNotRenamed());
    // API entry is kept, hence @Metadata exists.
    KmClassSubject kmClass = kept.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(kept.getFinalDescriptor(), getDescriptorFromKotlinClassifier(kmClass.getName()));
    // @Anno is kept.
    String annoName = pkg + ".anno.Anno";
    AnnotationSubject anno = kept.annotation(annoName);
    assertThat(anno, isPresent());

    ClassSubject renamed = inspector.clazz(pkg + ".anno.Renamed");
    assertThat(renamed, isPresentAndRenamed());
    // @Anno is kept.
    anno = renamed.annotation(annoName);
    assertThat(anno, isPresent());
    // @Metadata is kept even though the class is renamed.
    kmClass = renamed.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(
        renamed.getFinalDescriptor(), getDescriptorFromKotlinClassifier(kmClass.getName()));
  }
}
