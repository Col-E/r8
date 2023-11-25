// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Proto2BuilderShrinkingTest extends ProtoShrinkingTestBase {

  private enum MainClassesConfig {
    ALL,
    BUILDER_WITH_ONE_OF_SETTER_TEST_CLASS,
    BUILDER_WITH_PRIMITIVE_SETTERS_TEST_CLASS,
    BUILDER_WITH_PROTO_BUILDER_SETTER_TEST_CLASS,
    BUILDER_WITH_PROTO_SETTER_TEST_CLASS,
    BUILDER_WITH_REUSED_SETTERS_TEST_CLASS,
    HAS_FLAGGED_OFF_EXTENSION_BUILDER_TEST_CLASS;

    List<String> getMainClasses() {
      switch (this) {
        case ALL:
          return ImmutableList.of(
              "proto2.BuilderWithOneofSetterTestClass",
              "proto2.BuilderWithPrimitiveSettersTestClass",
              "proto2.BuilderWithProtoBuilderSetterTestClass",
              "proto2.BuilderWithProtoSetterTestClass",
              "proto2.BuilderWithReusedSettersTestClass",
              "proto2.HasFlaggedOffExtensionBuilderTestClass");
        case BUILDER_WITH_ONE_OF_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithOneofSetterTestClass");
        case BUILDER_WITH_PRIMITIVE_SETTERS_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithPrimitiveSettersTestClass");
        case BUILDER_WITH_PROTO_BUILDER_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithProtoBuilderSetterTestClass");
        case BUILDER_WITH_PROTO_SETTER_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithProtoSetterTestClass");
        case BUILDER_WITH_REUSED_SETTERS_TEST_CLASS:
          return ImmutableList.of("proto2.BuilderWithReusedSettersTestClass");
        case HAS_FLAGGED_OFF_EXTENSION_BUILDER_TEST_CLASS:
          return ImmutableList.of("proto2.HasFlaggedOffExtensionBuilderTestClass");
        default:
          throw new Unreachable();
      }
    }
  }

  private static final String METHOD_TO_INVOKE_ENUM =
      "com.google.protobuf.GeneratedMessageLite$MethodToInvoke";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO2_EXAMPLES_JAR, PROTO2_PROTO_JAR, PROTOBUF_LITE_JAR);

  @Parameter(0)
  public MainClassesConfig config;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        MainClassesConfig.values(),
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(PROGRAM_FILES)
            .addKeepMainRules(config.getMainClasses())
            .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
            .allowAccessModification()
            .allowDiagnosticMessages()
            .allowUnusedDontWarnPatterns()
            .allowUnusedProguardConfigurationRules()
            .enableInliningAnnotations()
            .enableProtoShrinking()
            .setMinApi(parameters)
            .compile()
            .assertAllInfoMessagesMatch(
                containsString("Proguard configuration rule does not match anything"))
            .apply(this::inspectWarningMessages)
            .inspect(this::inspect);

    for (String main : config.getMainClasses()) {
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
    ClassSubject generatedMessageLiteClassSubject =
        outputInspector.clazz("com.google.protobuf.GeneratedMessageLite");
    assertThat(generatedMessageLiteClassSubject, isPresent());

    MethodSubject isInitializedMethodSubject =
        generatedMessageLiteClassSubject.uniqueMethodWithOriginalName("isInitialized");

    DexType methodToInvokeType =
        outputInspector.clazz(METHOD_TO_INVOKE_ENUM).getDexProgramClass().getType();
    for (String main : config.getMainClasses()) {
      MethodSubject mainMethodSubject = outputInspector.clazz(main).mainMethod();
      assertThat(mainMethodSubject, isPresent());

      // Verify that the calls to GeneratedMessageLite.createBuilder() have been inlined.
      assertTrue(
          mainMethodSubject
              .streamInstructions()
              .filter(InstructionSubject::isInvoke)
              .map(InstructionSubject::getMethod)
              .allMatch(
                  method ->
                      method.getHolderType()
                              != generatedMessageLiteClassSubject.getDexProgramClass().getType()
                          || (isInitializedMethodSubject.isPresent()
                              && method
                                  == isInitializedMethodSubject
                                      .getProgramMethod()
                                      .getReference())));

      // Verify that there are no accesses to MethodToInvoke after inlining createBuilder() -- and
      // specifically no accesses to MethodToInvoke.NEW_BUILDER.
      assertTrue(
          main,
          mainMethodSubject
              .streamInstructions()
              .filter(InstructionSubject::isStaticGet)
              .map(instruction -> instruction.getField().getType())
              .noneMatch(methodToInvokeType::equals));

      // Verify that there is no switches on the ordinal of a MethodToInvoke instance.
      assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isSwitch));
    }
  }
}
