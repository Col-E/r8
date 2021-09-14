// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.ToolHelper.KotlinTargetVersion.JAVA_8;
import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinReflectJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteUnitPrimitiveTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".unit_primitive_lib";
  private static final String PKG_APP = PKG + ".unit_primitive_app";
  private final TestParameters parameters;

  private static final String EXPECTED =
      StringUtils.lines(
          "fun " + PKG_LIB + ".Lib.testInt(): kotlin.Int",
          "42",
          "fun " + PKG_LIB + ".Lib.testIntArray(): kotlin.IntArray",
          "42",
          "fun " + PKG_LIB + ".Lib.testUInt(): kotlin.UInt",
          "42",
          "fun " + PKG_LIB + ".Lib.testUIntArray(): kotlin.UIntArray",
          "UIntArray(storage=[42])",
          "fun " + PKG_LIB + ".Lib.testUnit(): kotlin.Unit",
          "testUnit",
          "kotlin.Unit");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withTargetVersion(JAVA_8)
            .build());
  }

  public MetadataRewriteUnitPrimitiveTest(
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
            getKotlinStdlibJar(kotlinc),
            getKotlinReflectJar(kotlinc),
            kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(getKotlinAnnotationJar(kotlinc))
            .addProgramFiles(
                kotlincLibJar.getForConfiguration(kotlinc, targetVersion),
                getKotlinStdlibJar(kotlinc))
            .addKeepClassAndMembersRules(PKG_LIB + ".*")
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .addKeepKotlinMetadata()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
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
        .addRunClasspathFiles(
            getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinReflectJar(kotlinc), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        // TODO(b/196179629): Should not fail.
        .assertFailureWithErrorThatMatches(
            containsString("Could not compute caller for function: public final fun testUnit()"));
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
      // TODO(b/196179629): There should not be any rewriting of the data since the return type
      //  should not change. Therefore we should be able to assert everything to be equal.
      Assert.assertNotEquals(originalHeader.getData1(), rewrittenHeader.getData1());
      Assert.assertNotEquals(originalHeader.getData2(), rewrittenHeader.getData2());
    }
  }
}
