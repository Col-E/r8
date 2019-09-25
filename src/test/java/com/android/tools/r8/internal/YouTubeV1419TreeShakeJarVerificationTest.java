// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.ToolHelper.isLocalDevelopment;
import static com.android.tools.r8.ToolHelper.shouldRunSlowTests;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.analysis.ProtoApplicationStats;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeV1419TreeShakeJarVerificationTest extends YouTubeCompilationBase {

  private static final int MAX_SIZE = 27500000;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public YouTubeV1419TreeShakeJarVerificationTest(TestParameters parameters) {
    super(14, 19);
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/141603168): Enable this on the bots.
    assumeTrue(isLocalDevelopment());
    assumeTrue(shouldRunSlowTests());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addKeepRuleFiles(getKeepRuleFiles())
            .addOptionsModification(
                options -> {
                  assert !options.enableFieldBitAccessAnalysis;
                  options.enableFieldBitAccessAnalysis = true;

                  assert !options.enableGeneratedExtensionRegistryShrinking;
                  options.enableGeneratedExtensionRegistryShrinking = true;

                  assert !options.enableGeneratedMessageLiteShrinking;
                  options.enableGeneratedMessageLiteShrinking = true;

                  assert !options.enableStringSwitchConversion;
                  options.enableStringSwitchConversion = true;
                })
            .allowUnusedProguardConfigurationRules()
            .compile();

    if (ToolHelper.isLocalDevelopment()) {
      DexItemFactory dexItemFactory = new DexItemFactory();
      ProtoApplicationStats original =
          new ProtoApplicationStats(dexItemFactory, new CodeInspector(getProgramFiles()));
      ProtoApplicationStats actual =
          new ProtoApplicationStats(dexItemFactory, compileResult.inspector(), original);
      ProtoApplicationStats baseline =
          new ProtoApplicationStats(
              dexItemFactory,
              new CodeInspector(getReleaseApk(), getReleaseProguardMap().toString()));
      System.out.println(actual.getStats(baseline));
    }

    int applicationSize = applicationSize(compileResult.app);
    System.out.println(applicationSize);

    assertTrue(
        "Expected max size of " + MAX_SIZE + ", got " + applicationSize,
        applicationSize < MAX_SIZE);
  }
}
