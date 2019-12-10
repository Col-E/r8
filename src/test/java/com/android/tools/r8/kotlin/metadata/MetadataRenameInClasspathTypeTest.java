// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameInClasspathTypeTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameInClasspathTypeTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Path baseLibJar;
  private static Path extLibJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String baseLibFolder = PKG_PREFIX + "/classpath_lib_base";
    baseLibJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addSourceFiles(getKotlinFileInTest(baseLibFolder, "itf"))
            .compile();
    String extLibFolder = PKG_PREFIX + "/classpath_lib_ext";
    extLibJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(baseLibJar)
            .addSourceFiles(getKotlinFileInTest(extLibFolder, "impl"))
            .compile();
  }

  @Test
  public void testMetadataInClasspathType_merged() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addClasspathFiles(baseLibJar)
            .addProgramFiles(extLibJar)
            // Keep the Extra class and its interface (which has the method).
            .addKeepRules("-keep class **.Extra")
            // Keep the ImplKt extension method which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.ImplKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String implClassName = pkg + ".classpath_lib_ext.Impl";
    final String extraClassName = pkg + ".classpath_lib_ext.Extra";
    compileResult.inspect(inspector -> {
      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, not(isPresent()));

      ClassSubject extra = inspector.clazz(extraClassName);
      assertThat(extra, isPresent());
      assertThat(extra, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = extra.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Impl")));
      // Can't build ClassSubject with Itf in classpath. Instead, check if the reference to Itf is
      // not altered via descriptors.
      List<String> superTypeDescriptors = kmClass.getSuperTypeDescriptors();
      assertTrue(superTypeDescriptors.stream().noneMatch(supertype -> supertype.contains("Impl")));
      assertTrue(superTypeDescriptors.stream().anyMatch(supertype -> supertype.contains("Itf")));
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/classpath_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(baseLibJar, libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), baseLibJar, libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".classpath_app.MainKt")
        .assertSuccessWithOutputLines("Impl::foo");
  }

  @Test
  public void testMetadataInClasspathType_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addClasspathFiles(baseLibJar)
            .addProgramFiles(extLibJar)
            // Keep the Extra class and its interface (which has the method).
            .addKeepRules("-keep class **.Extra")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Impl")
            // Keep the ImplKt extension method which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.ImplKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String implClassName = pkg + ".classpath_lib_ext.Impl";
    final String extraClassName = pkg + ".classpath_lib_ext.Extra";
    compileResult.inspect(inspector -> {
      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, isPresent());
      assertThat(impl, isRenamed());

      ClassSubject extra = inspector.clazz(extraClassName);
      assertThat(extra, isPresent());
      assertThat(extra, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = extra.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Impl")));
      assertTrue(superTypes.stream().anyMatch(
          supertype -> supertype.getFinalDescriptor().equals(impl.getFinalDescriptor())));
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/classpath_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(baseLibJar, libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), baseLibJar, libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".classpath_app.MainKt")
        .assertSuccessWithOutputLines("Impl::foo");
  }
}
