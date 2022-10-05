// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JumboStringTest extends SmaliTestBase {
  private static Pair<StringBuilder, StringBuilder> builders;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withOnlyDexRuntimeApiLevel().build();
  }

  @BeforeClass
  public static void createBuilders() {
    StringBuilder builder = new StringBuilder();
    StringBuilder expectedBuilder = new StringBuilder();
    builder.append("    new-instance         v0, Ljava/lang/StringBuilder;");
    builder.append("    invoke-direct        { v0 }, Ljava/lang/StringBuilder;-><init>()V");
    for (int i = 0; i <= 0xffff + 2; i++) {
      String prefixed = StringUtils.zeroPrefix(i, 5);
      expectedBuilder.append(prefixed);
      expectedBuilder.append(StringUtils.lines(""));
      builder.append("  const-string         v1, \"" + prefixed + "\\n\"");
      builder.append(
          "  invoke-virtual       { v0, v1 }, Ljava/lang/StringBuilder;"
              + "->append(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    }
    builder.append(
        "    invoke-virtual       { v0 }, Ljava/lang/StringBuilder;"
            + "->toString()Ljava/lang/String;");
    builder.append(StringUtils.lines("    move-result-object   v0"));
    builder.append(StringUtils.lines("    return-object        v0"));
    builders = new Pair<>(builder, expectedBuilder);
  }

  @Test
  public void test() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    smaliBuilder.addStaticMethod(
        "java.lang.String",
        DEFAULT_METHOD_NAME,
        ImmutableList.of(),
        2,
        builders.getFirst().toString()
    );

    smaliBuilder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-static       {}, LTest;->method()Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void"
    );

    testForR8(parameters.getBackend())
        .addProgramDexFileData(smaliBuilder.compile())
        .addKeepMainRule(DEFAULT_CLASS_NAME)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            options -> {
              options.enableStringConcatenationOptimization = false;
            })
        .run(parameters.getRuntime(), DEFAULT_CLASS_NAME)
        .assertSuccessWithOutput(builders.getSecond().toString())
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(DEFAULT_CLASS_NAME);
              assertThat(main, isPresent());
              MethodSubject method = main.uniqueMethodWithOriginalName(DEFAULT_METHOD_NAME);
              assertThat(method, isPresent());
              assertTrue(method.streamInstructions().anyMatch(InstructionSubject::isJumboString));
            });
  }

  @Test
  public void test_addconfigurationdebugging() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    smaliBuilder.addStaticMethod(
        "java.lang.String",
        DEFAULT_METHOD_NAME,
        ImmutableList.of(),
        2,
        builders.getFirst().toString()
    );

    // Intentionally dead code, but will be kept due to -addconfigurationdebugging.
    smaliBuilder.addStaticMethod(
        "java.lang.String",
        DEFAULT_METHOD_NAME + "2",
        ImmutableList.of(),
        2,
        builders.getFirst().toString()
    );

    smaliBuilder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    invoke-static       {}, LTest;->method()Ljava/lang/String;",
        "    move-result-object  v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    return-void"
    );

    testForR8(parameters.getBackend())
        .addProgramDexFileData(smaliBuilder.compile())
        .addKeepMainRule(DEFAULT_CLASS_NAME)
        .addKeepRules("-addconfigurationdebugging")
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            options -> {
              options.enableStringConcatenationOptimization = false;
            })
        .run(parameters.getRuntime(), DEFAULT_CLASS_NAME)
        .assertSuccessWithOutput(builders.getSecond().toString())
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(DEFAULT_CLASS_NAME);
              assertThat(main, isPresent());
              MethodSubject method = main.uniqueMethodWithOriginalName(DEFAULT_METHOD_NAME);
              assertThat(method, isPresent());
              assertTrue(method.streamInstructions().anyMatch(InstructionSubject::isJumboString));
              MethodSubject method2 = main.uniqueMethodWithOriginalName(DEFAULT_METHOD_NAME + "2");
              assertThat(method2, isPresent());
              assertTrue(method2.streamInstructions().anyMatch(InstructionSubject::isJumboString));
            });
  }
}
