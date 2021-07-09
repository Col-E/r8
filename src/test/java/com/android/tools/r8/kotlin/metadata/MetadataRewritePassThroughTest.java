// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewritePassThroughTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().withNoneRuntime().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build());
  }

  private final TestParameters parameters;

  public MetadataRewritePassThroughTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testKotlinStdLib() throws Exception {
    assumeFalse(parameters.isNoneRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .setMinApi(parameters.getApiLevel())
        .addKeepAllClassesRule()
        .addKeepKotlinMetadata()
        .addKeepAttributes(
            ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            inspector ->
                assertEqualMetadata(
                    new CodeInspector(getKotlinStdlibJar(kotlinc)),
                    inspector,
                    (addedStrings, addedNonInitStrings) -> {
                      assertEquals(0, addedStrings.intValue());
                      assertEquals(0, addedNonInitStrings.intValue());
                    }));
  }

  @Test
  public void testKotlinStdLibD8() throws Exception {
    assumeTrue(parameters.isNoneRuntime());
    testForD8(Backend.DEX)
        .addProgramFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .setMinApi(AndroidApiLevel.B)
        // Enable record desugaring support to force a non-identity naming lens
        .addOptionsModification(
            options -> options.testing.enableExperimentalRecordDesugaring = true)
        .compile()
        .inspect(
            inspector ->
                assertEqualMetadata(
                    new CodeInspector(getKotlinStdlibJar(kotlinc)),
                    inspector,
                    (addedStrings, addedNonInitStrings) -> {
                      assertEquals(0, addedStrings.intValue());
                      assertEquals(0, addedNonInitStrings.intValue());
                    }));
  }
}
