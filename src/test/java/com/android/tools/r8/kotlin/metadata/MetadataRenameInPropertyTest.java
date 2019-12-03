// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameInPropertyTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameInPropertyTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Path propertyLibJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String propertyLibFolder = PKG_PREFIX + "/propertytype_lib";
    propertyLibJar = getStaticTemp().newFile("property_lib.jar").toPath();
    ProcessResult processResult =
        ToolHelper.runKotlinc(
            null,
            propertyLibJar,
            null,
            getKotlinFileInTest(propertyLibFolder, "lib")
        );
    assertEquals(0, processResult.exitCode);
  }

  @Test
  public void testMetadataInProperty_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(propertyLibJar)
            // Keep non-private members of Impl
            .addKeepRules("-keep public class **.Impl { !private *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String itfClassName = pkg + ".propertytype_lib.Itf";
    final String implClassName = pkg + ".propertytype_lib.Impl";
    compileResult.inspect(inspector -> {
      ClassSubject itf = inspector.clazz(itfClassName);
      assertThat(itf, isPresent());
      assertThat(itf, isRenamed());

      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      DexAnnotation metadata = retrieveMetadata(impl.getDexClass());
      assertNotNull(metadata);
      // TODO(b/70169921): should not refer to Itf
      assertThat(metadata.toString(), containsString("Itf"));
    });

    Path libJar = temp.newFile("lib.jar").toPath();
    compileResult.writeToZip(libJar);

    String appFolder = PKG_PREFIX + "/propertytype_app";
    ProcessResult processResult =
        kotlinc(parameters.getRuntime().asCf())
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();
    // TODO(b/70169921): should be able to compile!
    assertNotEquals(0, processResult.exitCode);
    assertThat(
        processResult.stderr,
        containsString("cannot access class '" + pkg + ".propertytype_lib.Itf'"));
  }
}
