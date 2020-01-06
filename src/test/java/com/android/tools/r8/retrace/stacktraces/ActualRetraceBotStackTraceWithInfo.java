// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class ActualRetraceBotStackTraceWithInfo extends ActualBotStackTraceBase {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationFailedException: Compilation failed to complete",
        "\tat com.android.tools.r8.BaseCommand$Builder.build(:6)",
        "\tat com.android.tools.r8.R8TestBuilder.internalCompile(R8TestBuilder.java:104)",
        "\tat com.android.tools.r8.R8TestBuilder.internalCompile(R8TestBuilder.java:29)",
        "\tat com.android.tools.r8.TestCompilerBuilder.compile(TestCompilerBuilder.java:89)",
        "\tat com.android.tools.r8.TestCompilerBuilder.run(TestCompilerBuilder.java:113)",
        "\tat com.android.tools.r8.TestBuilder.run(TestBuilder.java:49)",
        "\tat com.android.tools.r8.ir.optimize.classinliner.ClassInlinerTest.testCodeSample(ClassInlinerTest.java:289)",
        "",
        "Caused by:",
        "com.android.tools.r8.utils.b: Error: offset: 158, line: 2, column: 33, Unexpected"
            + " attribute at <no file>:2:33",
        "-keepattributes -keepattributes LineNumberTable",
        "                                ^",
        "\tat com.android.tools.r8.utils.t0.a(:21)",
        "\tat com.android.tools.r8.shaking.ProguardConfigurationParser.parse(:19)",
        "\tat com.android.tools.r8.R8Command$Builder.i(:16)",
        "\tat com.android.tools.r8.R8Command$Builder.b(:11)",
        "\tat com.android.tools.r8.R8Command$Builder.b(:1)",
        "\tat com.android.tools.r8.BaseCommand$Builder.build(:2)",
        "\t... 6 more");
  }

  @Override
  public String mapping() {
    return r8MappingFromGitSha("dab96bbe5948133f0ae6e0a88fc133464421cf47");
  }

  // The Pruning lines is debug info printed by the command line interface if one passes --info and
  // --regex=<DEFAULT_REGULAR_EXPRESSION> as options.
  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Pruning \tat com.android.tools.r8.utils.Reporter.error(Reporter.java:21) from result"
            + " because method is not defined on line number 21",
        "Pruning \tat com.android.tools.r8.utils.Reporter.error(Reporter.java:21) from result"
            + " because method is not defined on line number 21",
        "Pruning \tat com.android.tools.r8.utils.Reporter.fatalError(Reporter.java:21) from result"
            + " because method is not defined on line number 21",
        "Pruning \tat"
            + " com.android.tools.r8.utils.Reporter.addSuppressedExceptions(Reporter.java:21) from"
            + " result because method is not defined on line number 21",
        "Pruning \tat"
            + " com.android.tools.r8.shaking.ProguardConfigurationParser.parse(ProguardConfigurationParser.java:19)"
            + " from result because method is not defined on line number 19",
        "Pruning \tat"
            + " com.android.tools.r8.shaking.ProguardConfigurationParser.parse(ProguardConfigurationParser.java:19)"
            + " from result because method is not defined on line number 19",
        "Pruning \tat"
            + " com.android.tools.r8.shaking.ProguardConfigurationParser.parse(ProguardConfigurationParser.java:19)"
            + " from result because method is not in range on line number 19",
        "Pruning \tat"
            + " com.android.tools.r8.shaking.ProguardConfigurationParser.parse(ProguardConfigurationParser.java:19)"
            + " from result because method is not in range on line number 19",
        "Pruning \tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:11) from"
            + " result because method is not defined on line number 11",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.setDisableVerticalClassMerging(R8Command.java:11)"
            + " from result because method is not defined on line number 11",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfigurationFiles$4(R8Command.java:11)"
            + " from result because method is not defined on line number 11",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfiguration$6(R8Command.java:11)"
            + " from result because method is not defined on line number 11",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfiguration$6(R8Command.java:11)"
            + " from result because method is not defined on line number 11",
        "Pruning \tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:11) from"
            + " result because method is not in range on line number 11",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.setDisableVerticalClassMerging(R8Command.java:1)"
            + " from result because method is not defined on line number 1",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfigurationFiles$4(R8Command.java:1)"
            + " from result because method is not defined on line number 1",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfiguration$6(R8Command.java:1)"
            + " from result because method is not defined on line number 1",
        "Pruning \tat"
            + " com.android.tools.r8.R8Command$Builder.lambda$addProguardConfiguration$6(R8Command.java:1)"
            + " from result because method is not defined on line number 1",
        "Pruning \tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:1) from"
            + " result because method is not defined on line number 1",
        "Pruning \tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:1) from"
            + " result because method is not defined on line number 1",
        "com.android.tools.r8.CompilationFailedException: Compilation failed to complete",
        "\tat com.android.tools.r8.BaseCommand$Builder.build(BaseCommand.java:143)",
        "\tat com.android.tools.r8.R8TestBuilder.internalCompile(R8TestBuilder.java:104)",
        "\tat com.android.tools.r8.R8TestBuilder.internalCompile(R8TestBuilder.java:29)",
        "\tat com.android.tools.r8.TestCompilerBuilder.compile(TestCompilerBuilder.java:89)",
        "\tat com.android.tools.r8.TestCompilerBuilder.run(TestCompilerBuilder.java:113)",
        "\tat com.android.tools.r8.TestBuilder.run(TestBuilder.java:49)",
        "\tat com.android.tools.r8.ir.optimize.classinliner.ClassInlinerTest.testCodeSample(ClassInlinerTest.java:289)",
        "",
        "Caused by:",
        "com.android.tools.r8.utils.AbortException: Error: offset: 158, line: 2, column: 33,"
            + " Unexpected attribute at <no file>:2:33",
        "-keepattributes -keepattributes LineNumberTable",
        "                                ^",
        "\tat com.android.tools.r8.utils.Reporter.failIfPendingErrors(Reporter.java:101)",
        "\tat com.android.tools.r8.shaking.ProguardConfigurationParser.parse(ProguardConfigurationParser.java:187)",
        "\tat com.android.tools.r8.R8Command$Builder.makeR8Command(R8Command.java:432)",
        "\tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:413)",
        "\tat com.android.tools.r8.R8Command$Builder.makeCommand(R8Command.java:61)",
        "\tat com.android.tools.r8.BaseCommand$Builder.build(BaseCommand.java:139)",
        "\t... 6 more");
  }

  @Override
  public int expectedWarnings() {
    return 1;
  }
}
