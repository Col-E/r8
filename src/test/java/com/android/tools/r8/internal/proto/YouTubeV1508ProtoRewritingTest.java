// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.ToolHelper.shouldRunSlowTests;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.assertRewrittenProtoSchemasMatch;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepAllProtosRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepDynamicMethodSignatureRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepNewMessageInfoSignatureRule;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.internal.LibrarySanitizer;
import com.android.tools.r8.internal.YouTubeCompilationBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeV1508ProtoRewritingTest extends YouTubeCompilationBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public YouTubeV1508ProtoRewritingTest(TestParameters parameters) {
    super(15, 8);
  }

  @Test
  public void test() throws Exception {
    assumeTrue(shouldRunSlowTests());

    LibrarySanitizer librarySanitizer =
        new LibrarySanitizer(temp)
            .addProgramFiles(getProgramFiles())
            .addLibraryFiles(getLibraryFiles())
            .sanitize()
            .assertSanitizedProguardConfigurationIsEmpty();

    testForR8(Backend.DEX)
        .addProgramFiles(getProgramFiles())
        .addLibraryFiles(librarySanitizer.getSanitizedLibrary())
        .addKeepRuleFiles(getKeepRuleFiles())
        .addKeepRules(
            keepAllProtosRule(),
            keepDynamicMethodSignatureRule(),
            keepNewMessageInfoSignatureRule())
        .addMainDexRuleFiles(getMainDexRuleFiles())
        .allowCheckDiscardedErrors(true)
        .allowDiagnosticMessages()
        .allowUnusedProguardConfigurationRules()
        .setMinApi(AndroidApiLevel.H_MR2)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertRewrittenProtoSchemasMatch(new CodeInspector(getProgramFiles()), inspector);
  }
}
