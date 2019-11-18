// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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

  private static final String LITE_BUILDER = "com.google.protobuf.GeneratedMessageLite$Builder";

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
            ImmutableList.of(
                "proto2.BuilderWithOneofSetterTestClass",
                "proto2.BuilderWithPrimitiveSettersTestClass",
                "proto2.BuilderWithProtoBuilderSetterTestClass",
                "proto2.BuilderWithProtoSetterTestClass",
                "proto2.BuilderWithReusedSettersTestClass")),
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
            .addOptionsModification(
                options -> {
                  options.applyInliningToInlinee = true;
                  options.enableFieldBitAccessAnalysis = true;
                  options.protoShrinking().enableGeneratedExtensionRegistryShrinking = true;
                  options.protoShrinking().enableGeneratedMessageLiteShrinking = true;
                  options.protoShrinking().enableGeneratedMessageLiteBuilderShrinking = true;
                  options.enableStringSwitchConversion = true;
                })
            .allowAccessModification()
            .allowUnusedProguardConfigurationRules()
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::inspect);

    // TODO(b/112437944): Should never allow dynamicMethod() to be inlined unless MethodToInvoke is
    //  guaranteed to be different from MethodToInvoke.BUILD_MESSAGE_INFO.
    assumeTrue(mains.size() > 1);

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
      default:
        throw new Unreachable();
    }
  }

  private void inspect(CodeInspector outputInspector) {
    // TODO(b/112437944): Should only be present if proto2.BuilderWithReusedSettersTestClass.main()
    //  is kept.
    assertThat(outputInspector.clazz(LITE_BUILDER), isPresent());

    // TODO(b/112437944): Should be absent.
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$NestedMessage$Builder"),
        isNestedMessageBuilderUsed(mains) ? isPresent() : not(isPresent()));

    // TODO(b/112437944): Should be absent.
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$OuterMessage$Builder"),
        isOuterMessageBuilderUsed(mains) ? isPresent() : not(isPresent()));

    // TODO(b/112437944): Should only be present if proto2.BuilderWithReusedSettersTestClass.main()
    //  is kept.
    assertThat(
        outputInspector.clazz("com.android.tools.r8.proto2.TestProto$Primitives$Builder"),
        isPrimitivesBuilderUsed(mains) ? isPresent() : not(isPresent()));
  }

  private static boolean isNestedMessageBuilderUsed(List<String> mains) {
    return mains.contains("proto2.BuilderWithProtoBuilderSetterTestClass")
        || mains.contains("proto2.BuilderWithProtoSetterTestClass");
  }

  private static boolean isOuterMessageBuilderUsed(List<String> mains) {
    return isNestedMessageBuilderUsed(mains);
  }

  private static boolean isPrimitivesBuilderUsed(List<String> mains) {
    return mains.contains("proto2.BuilderWithOneofSetterTestClass")
        || mains.contains("proto2.BuilderWithPrimitiveSettersTestClass")
        || mains.contains("proto2.BuilderWithReusedSettersTestClass");
  }
}
