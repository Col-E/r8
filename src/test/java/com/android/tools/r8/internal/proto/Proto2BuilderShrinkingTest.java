// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
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

@RunWith(Parameterized.class)
public class Proto2BuilderShrinkingTest extends ProtoShrinkingTestBase {

  private static final String LITE_BUILDER = "com.google.protobuf.GeneratedMessageLite$Builder";
  private static final String TEST_CLASS = "proto2.BuilderTestClass";

  private static List<Path> PROGRAM_FILES =
      ImmutableList.of(PROTO2_EXAMPLES_JAR, PROTO2_PROTO_JAR, PROTOBUF_LITE_JAR);

  private final boolean enableMinification;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, enable minification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public Proto2BuilderShrinkingTest(boolean enableMinification, TestParameters parameters) {
    this.enableMinification = enableMinification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PROGRAM_FILES)
        .addKeepMainRule(TEST_CLASS)
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        .addKeepRules(alwaysInlineNewSingularGeneratedExtensionRule())
        .addKeepRules("-neverinline class " + TEST_CLASS + " { <methods>; }")
        .addOptionsModification(
            options -> {
              options.enableFieldBitAccessAnalysis = true;
              options.protoShrinking().enableGeneratedExtensionRegistryShrinking = true;
              options.protoShrinking().enableGeneratedMessageLiteShrinking = true;
              options.protoShrinking().enableGeneratedMessageLiteBuilderShrinking = true;
              options.enableStringSwitchConversion = true;
            })
        .allowAccessModification()
        .allowUnusedProguardConfigurationRules()
        .enableInliningAnnotations()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TEST_CLASS)
        .assertSuccessWithOutputLines(
            "builderWithPrimitiveSetters",
            "17",
            "16",
            "builderWithReusedSetters",
            "1",
            "qux",
            "builderWithProtoBuilderSetter",
            "42",
            "builderWithProtoSetter",
            "42",
            "builderWithOneofSetter",
            "foo");
  }

  private void inspect(CodeInspector outputInspector) {
    ClassSubject liteClassSubject = outputInspector.clazz(LITE_BUILDER);
    assertThat(liteClassSubject, isPresent());

    MethodSubject copyOnWriteMethodSubject = liteClassSubject.uniqueMethodWithName("copyOnWrite");
    assertThat(copyOnWriteMethodSubject, isPresent());

    ClassSubject testClassSubject = outputInspector.clazz(TEST_CLASS);
    assertThat(testClassSubject, isPresent());

    List<String> testNames =
        ImmutableList.of(
            "builderWithPrimitiveSetters",
            "builderWithReusedSetters",
            "builderWithProtoBuilderSetter",
            "builderWithProtoSetter",
            "builderWithOneofSetter");
    for (String testName : testNames) {
      MethodSubject methodSubject = testClassSubject.uniqueMethodWithName(testName);
      assertThat(methodSubject, isPresent());
      assertTrue(
          methodSubject
              .streamInstructions()
              .filter(InstructionSubject::isInvoke)
              .map(InstructionSubject::getMethod)
              // TODO(b/112437944): Only builderWithReusedSetters() should invoke copyOnWrite().
              .anyMatch(method -> method == copyOnWriteMethodSubject.getMethod().method));
    }
  }
}
