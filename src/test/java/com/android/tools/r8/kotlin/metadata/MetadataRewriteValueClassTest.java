// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion.JAVA_8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import junit.framework.TestCase;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteValueClassTest extends KotlinMetadataTestBase {

  private static final String EXPECTED = StringUtils.lines("Hello, John Doe", "42", "UInt(x=42)");
  private static final String PKG_LIB = PKG + ".value_class_lib";
  private static final String PKG_APP = PKG + ".value_class_app";
  private final TestParameters parameters;
  private static final KotlinCompilerVersion MIN_SUPPORTED_KOTLIN_VERSION = KOTLINC_1_5_0;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withOldCompilersStartingFrom(MIN_SUPPORTED_KOTLIN_VERSION)
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_KOTLIN_VERSION)
            .withTargetVersion(JAVA_8)
            .build());
  }

  public MetadataRewriteValueClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer kotlincLibJar =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .addOptionsModification(
                internalOptions -> {
                  internalOptions.testing.keepMetadataInR8IfNotRewritten = false;
                })
            .compile()
            .inspect(this::inspect)
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) throws IOException {
    CodeInspector stdLibInspector =
        new CodeInspector(kotlincLibJar.getForConfiguration(kotlinc, targetVersion));
    for (FoundClassSubject clazzSubject : stdLibInspector.allClasses()) {
      ClassSubject r8Clazz = inspector.clazz(clazzSubject.getOriginalName());
      assertThat(r8Clazz, isPresent());
      KotlinClassMetadata originalMetadata = clazzSubject.getKotlinClassMetadata();
      KotlinClassMetadata rewrittenMetadata = r8Clazz.getKotlinClassMetadata();
      if (originalMetadata == null) {
        assertNull(rewrittenMetadata);
        continue;
      }
      TestCase.assertNotNull(rewrittenMetadata);
      KotlinClassHeader originalHeader = originalMetadata.getHeader();
      KotlinClassHeader rewrittenHeader = rewrittenMetadata.getHeader();
      TestCase.assertEquals(originalHeader.getKind(), rewrittenHeader.getKind());
      TestCase.assertEquals(originalHeader.getPackageName(), rewrittenHeader.getPackageName());
      // We cannot assert equality of the data since it may be ordered differently. Instead we use
      // the KotlinMetadataWriter.
      String expected = KotlinMetadataWriter.kotlinMetadataToString("", originalMetadata);
      String actual = KotlinMetadataWriter.kotlinMetadataToString("", rewrittenMetadata);
      TestCase.assertEquals(expected, actual);
      if (r8Clazz.getFinalName().equals(PKG_LIB + ".Name")) {
        assertThat(actual, containsString("inlineClassUnderlyingPropertyName"));
        assertThat(actual, containsString("inlineClassUnderlyingType"));
      }
    }
  }
}
