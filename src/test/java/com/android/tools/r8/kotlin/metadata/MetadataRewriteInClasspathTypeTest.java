// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInClasspathTypeTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("Impl::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInClasspathTypeTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer baseLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/classpath_lib_base", "itf"));
  private static final KotlinCompileMemoizer extLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/classpath_lib_ext", "impl"))
          .configure(
              kotlinCompilerTool -> {
                kotlinCompilerTool.addClasspathFiles(
                    baseLibJarMap.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });

  @Test
  public void smokeTest() throws Exception {
    Path baseLibJar = baseLibJarMap.getForConfiguration(kotlinc, targetVersion);
    Path extLibJar = extLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(baseLibJar, extLibJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/classpath_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), baseLibJar, extLibJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".classpath_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInClasspathType_renamed() throws Exception {
    Path baseLibJar = baseLibJarMap.getForConfiguration(kotlinc, targetVersion);
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                baseLibJar, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(extLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the Extra class and its interface (which has the method).
            .addKeepRules("-keep class **.Extra")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Impl")
            // Keep the ImplKt extension method which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.ImplKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(baseLibJar, libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/classpath_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), baseLibJar, libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".classpath_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectRenamed(CodeInspector inspector) {
    String implClassName = PKG + ".classpath_lib_ext.Impl";
    String implKtClassName = PKG + ".classpath_lib_ext.ImplKt";
    String extraClassName = PKG + ".classpath_lib_ext.Extra";

    ClassSubject impl = inspector.clazz(implClassName);
    assertThat(impl, isPresentAndRenamed());

    ClassSubject implKt = inspector.clazz(implKtClassName);
    assertThat(implKt, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = implKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("fooExt");
    assertThat(kmFunction, isExtensionFunction());

    ClassSubject extra = inspector.clazz(extraClassName);
    assertThat(extra, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = extra.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Impl")));
    assertTrue(superTypes.stream().anyMatch(
        supertype -> supertype.getFinalDescriptor().equals(impl.getFinalDescriptor())));
  }
}
