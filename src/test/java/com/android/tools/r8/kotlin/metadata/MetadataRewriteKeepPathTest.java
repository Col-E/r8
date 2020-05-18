// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteKeepPathTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0} target: {1}, keep: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        KotlinTargetVersion.values(),
        BooleanUtils.values());
  }

  private static Map<KotlinTargetVersion, Path> libJars = new HashMap<>();
  private static final String LIB_CLASS_NAME = PKG + ".box_primitives_lib.Test";
  private final TestParameters parameters;
  private final boolean keepMetadata;

  public MetadataRewriteKeepPathTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, boolean keepMetadata) {
    super(targetVersion);
    this.parameters = parameters;
    this.keepMetadata = keepMetadata;
  }

  @BeforeClass
  public static void createLibJar() throws Exception {
    String baseLibFolder = PKG_PREFIX + "/box_primitives_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(baseLibFolder, "lib"))
              .compile();
      libJars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void testProgramPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.get(targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .applyIf(keepMetadata, TestShrinkerBuilder::addKeepKotlinMetadata)
        .addKeepRuntimeVisibleAnnotations()
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(inspector -> inspect(inspector, keepMetadata));
  }

  @Test
  public void testClassPathPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.get(targetVersion))
        .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testLibraryPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.get(targetVersion))
        .addLibraryFiles(ToolHelper.getKotlinStdlibJar())
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testMissing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.get(targetVersion))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  private void inspect(CodeInspector inspector, boolean expectMetadata) {
    ClassSubject clazz = inspector.clazz(LIB_CLASS_NAME);
    assertThat(clazz, isPresent());
    assertEquals(expectMetadata, clazz.getKotlinClassMetadata() != null);
  }
}
