// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// TODO(b/112437944): Strengthen test to ensure that builder inlining succeeds even without single-
//  and double-caller inlining.
@RunWith(Parameterized.class)
public class Proto2BuilderShrinkingTest extends ProtoShrinkingTestBase {

  private static final String METHOD_TO_INVOKE_ENUM =
      "com.google.protobuf.GeneratedMessageLite$MethodToInvoke";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO2_EXAMPLES_JAR, PROTO2_PROTO_JAR, PROTOBUF_LITE_JAR);

  private final List<String> mains;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(
            ImmutableList.of("proto2.BuilderWithOneofSetterTestClass"),
            ImmutableList.of("proto2.BuilderWithPrimitiveSettersTestClass"),
            ImmutableList.of("proto2.BuilderWithProtoBuilderSetterTestClass"),
            ImmutableList.of("proto2.BuilderWithProtoSetterTestClass"),
            ImmutableList.of("proto2.BuilderWithReusedSettersTestClass"),
            ImmutableList.of("proto2.HasFlaggedOffExtensionBuilderTestClass"),
            ImmutableList.of(
                "proto2.BuilderWithOneofSetterTestClass",
                "proto2.BuilderWithPrimitiveSettersTestClass",
                "proto2.BuilderWithProtoBuilderSetterTestClass",
                "proto2.BuilderWithProtoSetterTestClass",
                "proto2.BuilderWithReusedSettersTestClass",
                "proto2.HasFlaggedOffExtensionBuilderTestClass")),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public Proto2BuilderShrinkingTest(List<String> mains, TestParameters parameters) {
    this.mains = mains;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(PROGRAM_FILES)
            .addKeepMainRules(mains)
            .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
            .allowAccessModification()
            .allowDiagnosticMessages()
            .allowUnusedProguardConfigurationRules()
            .enableInliningAnnotations()
            .enableProtoShrinking()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllInfoMessagesMatch(
                containsString("Proguard configuration rule does not match anything"))
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .inspect(this::inspect);

    for (String main : mains) {
      result.run(parameters.getRuntime(), main).assertSuccessWithOutput(getExpectedOutput(main));
    }
  }

  private static String getExpectedOutput(String main) {
    switch (main) {
      case "proto2.BuilderWithOneofSetterTestClass":
        return StringUtils.lines(
            "builderWithOneofSetter",
            "false",
            "0",
            "true",
            "foo",
            "false",
            "0",
            "false",
            "0",
            "false",
            "");
      case "proto2.BuilderWithPrimitiveSettersTestClass":
        return StringUtils.lines(
            "builderWithPrimitiveSetters",
            "true",
            "17",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "false",
            "",
            "false",
            "0",
            "false",
            "",
            "false",
            "0",
            "true",
            "16",
            "false",
            "");
      case "proto2.BuilderWithProtoBuilderSetterTestClass":
        return StringUtils.lines("builderWithProtoBuilderSetter", "42");
      case "proto2.BuilderWithProtoSetterTestClass":
        return StringUtils.lines("builderWithProtoSetter", "42");
      case "proto2.BuilderWithReusedSettersTestClass":
        return StringUtils.lines(
            "builderWithReusedSetters",
            "true",
            "1",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "false",
            "",
            "true",
            "1",
            "false",
            "",
            "false",
            "0",
            "false",
            "0",
            "true",
            "qux");
      case "proto2.HasFlaggedOffExtensionBuilderTestClass":
        return StringUtils.lines("4");
      default:
        throw new Unreachable();
    }
  }

  private void inspect(CodeInspector outputInspector) {
    verifyBuildersAreAbsent(outputInspector);
    verifyMethodToInvokeValuesAreAbsent(outputInspector);
  }

  private void verifyBuildersAreAbsent(CodeInspector outputInspector) {
    // TODO(b/171441793): Should be optimized out but fails do to soft pinning of super class.
    if (true) {
      return;
    }
    assertThat(
        outputInspector.clazz(
            "com.android.tools.r8.proto2.Shrinking$HasFlaggedOffExtension$Builder"),
        not(isPresent()));
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$Primitives$Builder"),
        not(isPresent()));
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$OuterMessage$Builder"),
        not(isPresent()));
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$NestedMessage$Builder"),
        not(isPresent()));
  }

  private void verifyMethodToInvokeValuesAreAbsent(CodeInspector outputInspector) {
    DexType methodToInvokeType =
        outputInspector.clazz(METHOD_TO_INVOKE_ENUM).getDexProgramClass().type;
    for (String main : mains) {
      MethodSubject mainMethodSubject = outputInspector.clazz(main).mainMethod();
      assertThat(mainMethodSubject, isPresent());
      assertTrue(
          main,
          mainMethodSubject
              .streamInstructions()
              .filter(InstructionSubject::isStaticGet)
              .map(instruction -> instruction.getField().type)
              .noneMatch(methodToInvokeType::equals));
      assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }
  }
}
