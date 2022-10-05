// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Proto3ShrinkingTest extends ProtoShrinkingTestBase {

  private static final String PARTIALLY_USED =
      "com.android.tools.r8.proto3.Shrinking$PartiallyUsed";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO3_EXAMPLES_JAR, PROTO3_PROTO_JAR, PROTOBUF_LITE_JAR);

  private final boolean allowAccessModification;
  private final boolean enableMinification;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{2}, allow access modification: {0}, enable minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build());
  }

  public Proto3ShrinkingTest(
      boolean allowAccessModification, boolean enableMinification, TestParameters parameters) {
    this.allowAccessModification = allowAccessModification;
    this.enableMinification = enableMinification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inputInspector = new CodeInspector(PROGRAM_FILES);
    testForR8(parameters.getBackend())
        .addProgramFiles(PROGRAM_FILES)
        .addKeepMainRule("proto3.TestClass")
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            outputInspector -> {
              verifyUnusedFieldsAreRemoved(inputInspector, outputInspector);
            })
        .run(parameters.getRuntime(), "proto3.TestClass")
        .assertSuccessWithOutputLines("--- partiallyUsed_proto3 ---", "42");
  }

  private void verifyUnusedFieldsAreRemoved(
      CodeInspector inputInspector, CodeInspector outputInspector) {
    // Verify that various proto fields are present the input.
    {
      ClassSubject puClassSubject = inputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertEquals(2, puClassSubject.allInstanceFields().size());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), isPresent());
    }

    // Verify that various proto fields have been removed in the output.
    {
      ClassSubject puClassSubject = outputInspector.clazz(PARTIALLY_USED);
      assertThat(puClassSubject, isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("used_"), isPresent());
      assertThat(puClassSubject.uniqueFieldWithOriginalName("completelyUnused_"), not(isPresent()));
    }
  }

  @Test
  public void testNoRewriting() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PROGRAM_FILES)
        .addKeepMainRule("proto3.TestClass")
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        // Retain all protos.
        .addKeepRules(keepAllProtosRule())
        // Retain the signature of dynamicMethod() and newMessageInfo().
        .addKeepRules(keepDynamicMethodSignatureRule(), keepNewMessageInfoSignatureRule())
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .assertAllWarningMessagesMatch(
            anyOf(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
                containsString("required for default or static interface methods desugaring")))
        .inspect(
            inspector ->
                assertRewrittenProtoSchemasMatch(new CodeInspector(PROGRAM_FILES), inspector));
  }
}
