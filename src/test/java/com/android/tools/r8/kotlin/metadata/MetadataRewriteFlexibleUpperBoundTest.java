// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import kotlinx.metadata.KmFlexibleTypeUpperBound;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteFlexibleUpperBoundTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("B.foo(): 42");
  private final String PKG_LIB = PKG + ".flexible_upper_bound_lib";

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteFlexibleUpperBoundTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> libJars = new HashMap<>();
  private final TestParameters parameters;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String baseLibFolder = PKG_PREFIX + "/flexible_upper_bound_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(baseLibFolder, "lib"))
              .compile();
      libJars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.get(targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/flexible_upper_bound_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".flexible_upper_bound_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(libJars.get(targetVersion))
            // Allow renaming A to ensure that we rename in the flexible upper bound type.
            .addKeepRules("-keep,allowobfuscation class " + PKG_LIB + ".A { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".B { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".FlexibleUpperBound { *; }")
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/flexible_upper_bound_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG + ".flexible_upper_bound_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) throws IOException, ExecutionException {
    // We are checking that A is renamed, and that the flexible upper bound information is
    // reflecting that.
    ClassSubject a = inspector.clazz(PKG_LIB + ".A");
    assertThat(a, isPresentAndRenamed());

    ClassSubject flexibleUpperBound = inspector.clazz(PKG_LIB + ".FlexibleUpperBound");
    assertThat(flexibleUpperBound, isPresentAndNotRenamed());

    List<KmPropertySubject> properties = flexibleUpperBound.getKmClass().getProperties();
    assertEquals(1, properties.size());
    KmPropertySubject kmPropertySubject = properties.get(0);
    KmTypeSubject returnTypeSubject = kmPropertySubject.returnType();
    assertThat(returnTypeSubject, isPresent());
    assertEquals(1, returnTypeSubject.typeArguments().size());
    KmTypeProjectionSubject argumentSubject = returnTypeSubject.typeArguments().get(0);
    KmFlexibleTypeUpperBound flexUpperBound = argumentSubject.type().getFlexibleUpperBound();
    assertNotNull(flexUpperBound);
    assertEquals(
        "Class(name=" + a.getFinalBinaryName() + ")",
        flexUpperBound.getType().classifier.toString());
  }
}
