// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.proto;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Proto3ShrinkingTest extends ProtoShrinkingTestBase {

  private final boolean allowAccessModification;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, allow access modification: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public Proto3ShrinkingTest(boolean allowAccessModification, TestParameters parameters) {
    this.allowAccessModification = allowAccessModification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PROTO3_EXAMPLES_JAR, PROTO3_PROTO_JAR, PROTOBUF_LITE_JAR)
        .addKeepMainRule("proto3.TestClass")
        .addKeepRules(
            // TODO(b/112437944): Use dex item based const strings for proto schema definitions.
            "-keepclassmembernames class * extends com.google.protobuf.GeneratedMessageLite {",
            "  <fields>;",
            "}",
            // TODO(b/112437944): Do not remove proto fields that are actually used in tree shaking.
            "-keepclassmembers,allowobfuscation class * extends",
            "    com.google.protobuf.GeneratedMessageLite {",
            "  <fields>;",
            "}",
            allowAccessModification ? "-allowaccessmodification" : "")
        .addKeepRuleFiles(PROTOBUF_LITE_PROGUARD_RULES)
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), "proto3.TestClass")
        .assertSuccessWithOutputLines("--- partiallyUsed_proto3 ---", "42");
  }
}
