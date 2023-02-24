// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInLibraryTypeTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("Sub::foo", "Sub::boo", "true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteInLibraryTypeTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer baseLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/libtype_lib_base", "base"));
  private static final KotlinCompileMemoizer extLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/libtype_lib_ext", "ext"))
          .configure(
              kotlinCompilerTool -> {
                kotlinCompilerTool.addClasspathFiles(
                    baseLibJarMap.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });
  private static final KotlinCompileMemoizer appJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/libtype_app", "main"))
          .configure(
              kotlinCompilerTool -> {
                kotlinCompilerTool.addClasspathFiles(
                    baseLibJarMap.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
                kotlinCompilerTool.addClasspathFiles(
                    extLibJarMap.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });

  @Test
  public void smokeTest() throws Exception {
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), baseLibJarMap.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(
            extLibJarMap.getForConfiguration(kotlinc, targetVersion),
            appJarMap.getForConfiguration(kotlinc, targetVersion))
        .run(parameters.getRuntime(), PKG + ".libtype_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    String main = PKG + ".libtype_app.MainKt";
    Path out =
        testForR8(parameters.getBackend())
            // Intentionally not providing baseLibJar as lib file nor classpath file.
            .addProgramFiles(
                extLibJarMap.getForConfiguration(kotlinc, targetVersion),
                appJarMap.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            // Keep Ext extension method which requires metadata to be called with Kotlin syntax
            // from other kotlin code.
            .addKeepRules("-keep class **.ExtKt { <methods>; }")
            // Keep the main entry.
            .addKeepMainRule(main)
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addDontWarn(PKG + ".**")
            .allowDiagnosticWarningMessages()
            // -dontoptimize so that basic code structure is kept.
            .addDontOptimize()
            .compile()
            .inspect(this::inspect)
            .assertAllWarningMessagesMatch(
                anyOf(
                    equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
                    equalTo("Resource 'META-INF/main.kotlin_module' already exists.")))
            .writeToZip();

    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), baseLibJarMap.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(out)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    String extClassName = PKG + ".libtype_lib_ext.ExtKt";
    ClassSubject ext = inspector.clazz(extClassName);
    assertThat(ext, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = ext.getKmPackage();
    assertThat(kmPackage, isPresent());
    // Type appearance of library type, Base, should be kept, even if it's not provided.
    // Note that the resulting ClassSubject for Base is an absent one as we don't provide it, and
    // thus we can't use `getReturnTypesInFunctions`, which filters out absent class subject.
    assertTrue(kmPackage.getReturnTypeDescriptorsInFunctions().stream().anyMatch(
        returnType -> returnType.contains("Base")));
  }
}
