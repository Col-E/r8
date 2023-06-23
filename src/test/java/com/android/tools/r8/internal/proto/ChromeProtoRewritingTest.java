// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.ToolHelper.shouldRunSlowTests;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.assertRewrittenProtoSchemasMatch;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepAllProtosRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepDynamicMethodSignatureRule;
import static com.android.tools.r8.internal.proto.ProtoShrinkingTestBase.keepNewMessageInfoSignatureRule;
import static com.android.tools.r8.utils.codeinspector.Matchers.invalidGenericArgumentApplicationCount;
import static com.android.tools.r8.utils.codeinspector.Matchers.proguardConfigurationRuleDoesNotMatch;
import static com.android.tools.r8.utils.codeinspector.Matchers.typeVariableNotInScope;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.internal.ChromeCompilationBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChromeProtoRewritingTest extends ChromeCompilationBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ChromeProtoRewritingTest(TestParameters parameters) {
    super(200430, false);
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    assumeTrue(shouldRunSlowTests());
    testForR8(Backend.DEX)
        .addProgramFiles(getProgramFiles())
        .addLibraryFiles(getLibraryFiles())
        .addKeepRuleFiles(getKeepRuleFiles())
        .addKeepRules(
            keepAllProtosRule(),
            keepDynamicMethodSignatureRule(),
            keepNewMessageInfoSignatureRule())
        .addDontWarn("android.content.pm.IPackageManager")
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking(false)
        .setMinApi(AndroidApiLevel.N)
        .allowDiagnosticInfoMessages()
        .compile()
        .inspectDiagnosticMessages(
            diagnostics ->
                diagnostics.assertAllInfosMatch(
                    anyOf(
                        typeVariableNotInScope(),
                        invalidGenericArgumentApplicationCount(),
                        proguardConfigurationRuleDoesNotMatch())))
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertRewrittenProtoSchemasMatch(new CodeInspector(getProgramFiles()), inspector);
  }
}
