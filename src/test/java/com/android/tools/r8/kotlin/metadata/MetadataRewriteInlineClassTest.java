// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_6_0;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import junit.framework.TestCase;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInlineClassTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("Password(s=Hello World!)");
  private final String passwordTypeName = PKG + ".inline_class_lib.Password";
  private static final KotlinCompilerVersion MIN_SUPPORTED_KOTLIN_VERSION = KOTLINC_1_6_0;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withOldCompilersStartingFrom(MIN_SUPPORTED_KOTLIN_VERSION)
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_KOTLIN_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInlineClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/inline_class_lib", "lib"));
  private final TestParameters parameters;

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/inline_class_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".inline_class_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addKeepClassAndMembersRules(passwordTypeName)
            // kotlinc will generate a method for the unboxed type on the form login-XXXXX(String).
            // Ideally, this should be targeted by annotation instead.
            .addKeepRules(
                "-keep class " + PKG + ".inline_class_lib.LibKt { *** login-*(java.lang.String); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/inline_class_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG + ".inline_class_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) throws IOException {
    CodeInspector stdLibInspector =
        new CodeInspector(libJars.getForConfiguration(kotlinc, targetVersion));
    ClassSubject clazzSubject = stdLibInspector.clazz(passwordTypeName);
    ClassSubject r8Clazz = inspector.clazz(clazzSubject.getOriginalName());
    assertThat(r8Clazz, isPresent());
    KotlinClassMetadata originalMetadata = clazzSubject.getKotlinClassMetadata();
    KotlinClassMetadata rewrittenMetadata = r8Clazz.getKotlinClassMetadata();
    TestCase.assertNotNull(rewrittenMetadata);
    KotlinClassHeader originalHeader = originalMetadata.getHeader();
    KotlinClassHeader rewrittenHeader = rewrittenMetadata.getHeader();
    TestCase.assertEquals(originalHeader.getKind(), rewrittenHeader.getKind());
    TestCase.assertEquals(originalHeader.getPackageName(), rewrittenHeader.getPackageName());
    Assert.assertArrayEquals(originalHeader.getData1(), rewrittenHeader.getData1());
    Assert.assertArrayEquals(originalHeader.getData2(), rewrittenHeader.getData2());
    String expected = KotlinMetadataWriter.kotlinMetadataToString("", originalMetadata);
    String actual = KotlinMetadataWriter.kotlinMetadataToString("", rewrittenMetadata);
    TestCase.assertEquals(expected, actual);
  }
}
