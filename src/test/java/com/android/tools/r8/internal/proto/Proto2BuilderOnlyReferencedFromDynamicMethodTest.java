// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Proto2BuilderOnlyReferencedFromDynamicMethodTest extends ProtoShrinkingTestBase {

  private static final String MAIN = "proto2.BuilderOnlyReferencedFromDynamicMethodTestClass";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO2_EXAMPLES_JAR, PROTO2_PROTO_JAR, PROTOBUF_LITE_JAR);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public Proto2BuilderOnlyReferencedFromDynamicMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PROGRAM_FILES)
        .addKeepMainRule(MAIN)
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        .allowAccessModification()
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .enableProtoShrinking()
        .setMinApi(parameters)
        .compile()
        .assertAllInfoMessagesMatch(
            containsString("Proguard configuration rule does not match anything"))
        .apply(this::inspectWarningMessages)
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(
            "false", "0", "false", "", "false", "0", "false", "0", "false", "");
  }

  private void inspect(CodeInspector outputInspector) {
    verifyBuilderIsAbsent(outputInspector);
  }

  private void verifyBuilderIsAbsent(CodeInspector outputInspector) {
    ClassSubject generatedMessageLiteBuilder =
        outputInspector.clazz("com.google.protobuf.GeneratedMessageLite$Builder");
    assertThat(generatedMessageLiteBuilder, isPresent());
    assertFalse(generatedMessageLiteBuilder.isAbstract());
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$Primitives$Builder"),
        not(isPresent()));
  }
}
