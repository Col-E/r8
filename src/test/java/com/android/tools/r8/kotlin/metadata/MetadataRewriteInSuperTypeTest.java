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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInSuperTypeTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInSuperTypeTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> superTypeLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String superTypeLibFolder = PKG_PREFIX + "/supertype_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path superTypeLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(superTypeLibFolder, "impl"),
                  getKotlinFileInTest(superTypeLibFolder + "/internal", "itf"))
              .compile();
      superTypeLibJarMap.put(targetVersion, superTypeLibJar);
    }
  }

  @Test
  public void testMetadataInSupertype_merged() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(superTypeLibJarMap.get(targetVersion))
            // Keep non-private members except for ones in `internal` definitions.
            .addKeepRules("-keep public class !**.internal.**, * { !private *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String itfClassName = pkg + ".supertype_lib.internal.Itf";
    final String implClassName = pkg + ".supertype_lib.Impl";
    compileResult.inspect(inspector -> {
      assertThat(inspector.clazz(itfClassName), not(isPresent()));

      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = impl.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("internal")));
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Itf")));
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/supertype_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".supertype_app.MainKt")
        .assertSuccessWithOutputLines("Impl::foo", "Program::foo");
  }

  @Test
  public void testMetadataInSupertype_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(superTypeLibJarMap.get(targetVersion))
            // Keep non-private members except for ones in `internal` definitions.
            .addKeepRules("-keep public class !**.internal.**, * { !private *; }")
            // Keep `internal` definitions, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.internal.** { *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String itfClassName = pkg + ".supertype_lib.internal.Itf";
    final String implClassName = pkg + ".supertype_lib.Impl";
    compileResult.inspect(inspector -> {
      ClassSubject itf = inspector.clazz(itfClassName);
      assertThat(itf, isRenamed());

      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = impl.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("internal")));
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Itf")));
      assertTrue(superTypes.stream().anyMatch(
          supertype -> supertype.getFinalDescriptor().equals(itf.getFinalDescriptor())));
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/supertype_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".supertype_app.MainKt")
        .assertSuccessWithOutputLines("Impl::foo", "Program::foo");
  }
}
