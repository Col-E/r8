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
import com.android.tools.r8.internal.YouTubeCompilationBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class YouTubeProtoRewritingTest extends YouTubeCompilationBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public YouTubeProtoRewritingTest(TestParameters parameters) {
    super(14, 19);
    this.parameters = parameters;
  }

  @Ignore
  @Test
  public void test() throws Exception {
    assumeTrue(shouldRunSlowTests());

    testForR8(parameters.getBackend())
        .addKeepRuleFiles(getKeepRuleFiles())
        // Retain all protos.
        .addKeepRules(keepAllProtosRule())
        // Retain the signature of dynamicMethod() and newMessageInfo().
        .addKeepRules(keepDynamicMethodSignatureRule(), keepNewMessageInfoSignatureRule())
        // Enable the dynamicMethod() rewritings.
        .addOptionsModification(
            options -> {
              assert !options.enableGeneratedMessageLiteShrinking;
              options.enableGeneratedMessageLiteShrinking = true;
            })
        .allowUnusedProguardConfigurationRules()
        .compile()
        .inspect(
            inspector ->
                assertRewrittenProtoSchemasMatch(new CodeInspector(getProgramFiles()), inspector));
  }
}
