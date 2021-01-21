// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewritePassThroughTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0}, target: {1}, kotlinc: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers());
  }

  private final TestParameters parameters;

  public MetadataRewritePassThroughTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, KotlinCompiler kotlinc) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
  }

  @Test
  public void testKotlinStdLib() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .setMinApi(parameters.getApiLevel())
        .addKeepAllClassesRule()
        .addKeepKotlinMetadata()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .addDontWarnJetBrainsAnnotations()
        .compile()
        .inspect(
            inspector ->
                assertEqualMetadata(
                    new CodeInspector(ToolHelper.getKotlinStdlibJar(kotlinc)), inspector));
  }
}
