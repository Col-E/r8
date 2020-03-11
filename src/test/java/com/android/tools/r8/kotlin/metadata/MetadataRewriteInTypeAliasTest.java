// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInTypeAliasTest extends KotlinMetadataTestBase {
  private static final String EXPECTED =
      StringUtils.lines("Impl::foo", "Program::foo", "true", "42");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInTypeAliasTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> typeAliasLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String typeAliasLibFolder = PKG_PREFIX + "/typealias_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path typeAliasLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(typeAliasLibFolder, "lib"))
              .compile();
      typeAliasLibJarMap.put(targetVersion, typeAliasLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = typeAliasLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typealias_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".typealias_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInTypeAlias_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(typeAliasLibJarMap.get(targetVersion))
            // Keep non-private members of Impl
            .addKeepRules("-keep class **.Impl { !private *; }")
            // Keep Itf, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Itf")
            // Keep LibKt that contains the type-aliases and utils.
            .addKeepRules("-keep class **.LibKt { *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typealias_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/151194785): update to just .compile() once fixed.
            .compileRaw();
    // TODO(b/151194785): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(
        kotlinTestCompileResult.stderr,
        containsString(
            "type mismatch: inferred type is ProgramClass but API /* = Itf */ was expected"));
  }

  private void inspect(CodeInspector inspector) {
    String itfClassName = PKG + ".typealias_lib.Itf";
    String libKtClassName = PKG + ".typealias_lib.LibKt";

    ClassSubject itf = inspector.clazz(itfClassName);
    assertThat(itf, isRenamed());

    ClassSubject libKt = inspector.clazz(libKtClassName);
    assertThat(libKt, isPresent());
    assertThat(libKt, not(isRenamed()));

    MethodSubject seq = libKt.uniqueMethodWithName("seq");
    assertThat(seq, isPresent());
    assertThat(seq, not(isRenamed()));

    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());
    // TODO(b/151194785): need further inspection: many kinds of type appearances in typealias.
  }
}
