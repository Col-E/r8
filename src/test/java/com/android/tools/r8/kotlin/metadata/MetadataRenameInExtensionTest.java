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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameInExtensionTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameInExtensionTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Path extLibJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String extLibFolder = PKG_PREFIX + "/extension_lib";
    extLibJar = getStaticTemp().newFile("ext_lib.jar").toPath();
    ProcessResult processResult =
        ToolHelper.runKotlinc(
            null,
            extLibJar,
            null,
            getKotlinFileInTest(extLibFolder, "B")
        );
    assertEquals(0, processResult.exitCode);
  }

  @Test
  public void testMetadataInExtension_merged() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(extLibJar)
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep the BKt extension method which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes("*Annotation*")
            .compile();
    String pkg = getClass().getPackage().getName();
    final String superClassName = pkg + ".extension_lib.Super";
    final String bClassName = pkg + ".extension_lib.B";
    compileResult.inspect(inspector -> {
      ClassSubject sup = inspector.clazz(superClassName);
      assertThat(sup, not(isPresent()));

      ClassSubject impl = inspector.clazz(bClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      DexAnnotation metadata = retrieveMetadata(impl.getDexClass());
      assertNotNull(metadata);
      assertThat(metadata.toString(), not(containsString("Super")));
    });

    Path r8ProcessedLibZip = temp.newFile("r8-lib.zip").toPath();
    compileResult.writeToZip(r8ProcessedLibZip);

    String appFolder = PKG_PREFIX + "/extension_app";
    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf())
            .addClasspathFiles(r8ProcessedLibZip)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/143687784): update to just .compile() once fixed.
            .compileRaw();
    // TODO(b/143687784): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(kotlinTestCompileResult.stderr, containsString("unresolved reference: doStuff"));
  }

  @Test
  public void testMetadataInExtension_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(extLibJar)
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super")
            // Keep the BKt extension method which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes("*Annotation*")
            .compile();
    String pkg = getClass().getPackage().getName();
    final String superClassName = pkg + ".extension_lib.Super";
    final String bClassName = pkg + ".extension_lib.B";
    compileResult.inspect(inspector -> {
      ClassSubject sup = inspector.clazz(superClassName);
      assertThat(sup, isPresent());
      assertThat(sup, isRenamed());

      ClassSubject impl = inspector.clazz(bClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      DexAnnotation metadata = retrieveMetadata(impl.getDexClass());
      assertNotNull(metadata);
      assertThat(metadata.toString(), not(containsString("Super")));
    });

    Path r8ProcessedLibZip = temp.newFile("r8-lib.zip").toPath();
    compileResult.writeToZip(r8ProcessedLibZip);

    String appFolder = PKG_PREFIX + "/extension_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf())
            .addClasspathFiles(r8ProcessedLibZip)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), r8ProcessedLibZip)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".extension_app.MainKt")
        .assertSuccessWithOutputLines("do stuff", "do stuff");
  }
}
