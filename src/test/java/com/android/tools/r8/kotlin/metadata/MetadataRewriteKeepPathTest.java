// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteKeepPathTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0} target: {1}, kotlinc: {2}, keep: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers(),
        BooleanUtils.values());
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/box_primitives_lib", "lib"));
  private static final String LIB_CLASS_NAME = PKG + ".box_primitives_lib.Test";
  private final TestParameters parameters;
  private final boolean keepMetadata;

  public MetadataRewriteKeepPathTest(
      TestParameters parameters,
      KotlinTargetVersion targetVersion,
      KotlinCompiler kotlinc,
      boolean keepMetadata) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
    this.keepMetadata = keepMetadata;
  }

  @Test
  public void testProgramPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .applyIf(keepMetadata, TestShrinkerBuilder::addKeepKotlinMetadata)
        .addKeepRuntimeVisibleAnnotations()
        .allowDiagnosticWarningMessages()
        .addDontWarnJetBrainsAnnotations()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(inspector -> inspect(inspector, keepMetadata));
  }

  @Test
  public void testClassPathPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .addDontWarnJetBrainsAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testLibraryPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addLibraryFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .addDontWarnJetBrainsAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testMissing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .addDontWarnJetBrainsAnnotations()
        .addDontWarnKotlin()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  private void inspect(CodeInspector inspector, boolean expectMetadata) {
    ClassSubject clazz = inspector.clazz(LIB_CLASS_NAME);
    assertThat(clazz, isPresent());
    assertEquals(expectMetadata, clazz.getKotlinClassMetadata() != null);
  }
}
