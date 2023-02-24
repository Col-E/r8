// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewritePrunedObjectsTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("42", "0", "Goodbye World");
  private static final String PKG_LIB = PKG + ".pruned_lib";
  private static final String PKG_APP = PKG + ".pruned_app";

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));
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

  public MetadataRewritePrunedObjectsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepRules(
                "-keep class " + PKG_LIB + ".Sub { <init>(); *** kept(); *** keptProperty; }")
            .addKeepRules("-neverinline class * { @" + PKG_LIB + ".NeverInline *; }")
            .addKeepClassAndMembersRules(PKG_LIB + ".SubUser")
            .addKeepRuntimeVisibleAnnotations()
            .enableProguardTestOptions()
            .addDontObfuscate()
            .compile()
            .inspect(this::checkPruned)
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addProgramFiles(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void checkPruned(CodeInspector inspector) {
    ClassSubject base = inspector.clazz(PKG_LIB + ".Base");
    assertThat(base, not(isPresent()));
    ClassSubject sub = inspector.clazz(PKG_LIB + ".Sub");
    assertThat(sub, isPresent());
    KmClassSubject kmClass = sub.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(0, kmClass.getSuperTypes().size());
    // Ensure that we do not prune the constructors.
    assertEquals(1, kmClass.getConstructors().size());
    // Assert that we have removed the metadata for a function that is removed.
    assertThat(kmClass.kmFunctionWithUniqueName("notKept"), not(isPresent()));
    assertThat(kmClass.kmFunctionWithUniqueName("keptWithoutPinning"), not(isPresent()));
    // Check that we have not pruned the property information for a kept field.
    assertThat(kmClass.kmPropertyWithUniqueName("keptProperty"), isPresent());
    assertThat(kmClass.kmPropertyWithUniqueName("notExposedProperty"), not(isPresent()));
  }
}
