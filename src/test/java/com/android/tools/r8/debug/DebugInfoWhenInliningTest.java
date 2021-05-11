// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.naming.ClassNameMapper.MissingFileAction;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests source file and line numbers on inlined methods. */
@RunWith(Parameterized.class)
public class DebugInfoWhenInliningTest extends DebugTestBase {

  private static final String CLASS_NAME = "Inlining1";
  private static final String SOURCE_FILE = "Inlining1.java";

  private DebugTestConfig makeConfig(
      LineNumberOptimization lineNumberOptimization, boolean writeProguardMap) throws Exception {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(DEBUGGEE_JAR)
            .addKeepMainRule(CLASS_NAME)
            .noMinification()
            .addKeepAttributeSourceFile()
            .addKeepAttributeLineNumberTable()
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(
                options -> {
                  options.lineNumberOptimization = lineNumberOptimization;
                  options.testing.forceJumboStringProcessing = forceJumboStringProcessing;
                  // TODO(b/117848700): Can we make these tests neutral to inlining threshold?
                  // Also CF needs improvements here.
                  options.inliningInstructionLimit = parameters.isCfRuntime() ? 5 : 4;
                })
            .compile();
    DebugTestConfig config = result.debugConfig();
    if (writeProguardMap) {
      config.setProguardMap(result.writeProguardMap(), MissingFileAction.MISSING_FILE_IS_EMPTY_MAP);
    }
    return config;
  }

  private final TestParameters parameters;
  private final boolean forceJumboStringProcessing;

  @Parameters(name = "{0}, force-jumbo: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParametersBuilder.builder().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values());
  }

  public DebugInfoWhenInliningTest(TestParameters parameters, boolean forceJumboString) {
    assumeTrue(!parameters.isCfRuntime() || !forceJumboString);
    this.parameters = parameters;
    this.forceJumboStringProcessing = forceJumboString;
  }

  private void assumeMappingIsNotToPCs() {
    assumeTrue(
        "Ignoring test when the line number table is removed.",
        parameters.isCfRuntime()
            || parameters.getApiLevel().isLessThan(apiLevelWithPcAsLineNumberSupport()));
  }

  @Test
  public void testEachLineNotOptimized() throws Throwable {
    assumeMappingIsNotToPCs();
    // The reason why the not-optimized test contains half as many line numbers as the optimized
    // one:
    //
    // In the Java source (Inlining1) each call is duplicated. Since they end up on the same line
    // (innermost callee) the line numbers are actually 7, 7, 32, 32, ... but even if the positions
    // are emitted duplicated in the dex code, the debugger stops only when there's a change.
    int[] lineNumbers = {7, 32, 11, 7};
    testEachLine(makeConfig(LineNumberOptimization.OFF, false), lineNumbers);
  }

  @Test
  public void testEachLineNotOptimizedWithMap() throws Throwable {
    assumeMappingIsNotToPCs();
    // The reason why the not-optimized test contains half as many line numbers as the optimized
    // one:
    //
    // In the Java source (Inlining1) each call is duplicated. Since they end up on the same line
    // (innermost callee) the line numbers are actually 7, 7, 32, 32, ... but even if the positions
    // are emitted duplicated in the dex code, the debugger stops only when there's a change.
    int[] lineNumbers = {7, 32, 11, 7};
    testEachLine(makeConfig(LineNumberOptimization.OFF, true), lineNumbers);
  }

  @Test
  public void testEachLineOptimized() throws Throwable {
    assumeMappingIsNotToPCs();
    int[] lineNumbers = {1, 2, 3, 4, 5, 6, 7, 8};
    testEachLine(makeConfig(LineNumberOptimization.ON, false), lineNumbers);
  }

  @Test
  public void testEachLineOptimizedWithMap() throws Throwable {
    assumeMappingIsNotToPCs();
    int[] lineNumbers = {7, 7, 32, 32, 11, 11, 7, 7};
    List<List<SignatureAndLine>> inlineFramesList =
        ImmutableList.of(
            ImmutableList.of(
                new SignatureAndLine("void inlineThisFromSameFile()", 7),
                new SignatureAndLine("void main(java.lang.String[])", 19)),
            ImmutableList.of(
                new SignatureAndLine("void inlineThisFromSameFile()", 7),
                new SignatureAndLine("void main(java.lang.String[])", 20)),
            ImmutableList.of(
                new SignatureAndLine("void Inlining2.inlineThisFromAnotherFile()", 32),
                new SignatureAndLine("void main(java.lang.String[])", 21)),
            ImmutableList.of(
                new SignatureAndLine("void Inlining2.inlineThisFromAnotherFile()", 32),
                new SignatureAndLine("void main(java.lang.String[])", 22)),
            ImmutableList.of(
                new SignatureAndLine("void sameFileMultilevelInliningLevel2()", 11),
                new SignatureAndLine("void sameFileMultilevelInliningLevel1()", 15),
                new SignatureAndLine("void main(java.lang.String[])", 23)),
            ImmutableList.of(
                new SignatureAndLine("void sameFileMultilevelInliningLevel2()", 11),
                new SignatureAndLine("void sameFileMultilevelInliningLevel1()", 15),
                new SignatureAndLine("void main(java.lang.String[])", 24)),
            ImmutableList.of(
                new SignatureAndLine("void Inlining3.differentFileMultilevelInliningLevel2()", 7),
                new SignatureAndLine("void Inlining2.differentFileMultilevelInliningLevel1()", 36),
                new SignatureAndLine("void main(java.lang.String[])", 25)),
            ImmutableList.of(
                new SignatureAndLine("void Inlining3.differentFileMultilevelInliningLevel2()", 7),
                new SignatureAndLine("void Inlining2.differentFileMultilevelInliningLevel1()", 36),
                new SignatureAndLine("void main(java.lang.String[])", 26)));
    testEachLine(makeConfig(LineNumberOptimization.ON, true), lineNumbers, inlineFramesList);
  }

  private void testEachLine(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    testEachLine(config, lineNumbers, null);
  }

  private void testEachLine(
      DebugTestConfig config, int[] lineNumbers, List<List<SignatureAndLine>> inlineFramesList)
      throws Throwable {
    final String mainSignature = "([Ljava/lang/String;)V";

    List<Command> commands = new ArrayList<Command>();
    commands.add(breakpoint(CLASS_NAME, "main", mainSignature));
    commands.add(run());
    boolean first = true;
    assert inlineFramesList == null || inlineFramesList.size() == lineNumbers.length;
    for (int idx = 0; idx < lineNumbers.length; ++idx) {
      if (first) {
        first = false;
      } else {
        commands.add(stepOver());
      }
      commands.add(checkMethod(CLASS_NAME, "main", mainSignature));
      commands.add(checkLine(SOURCE_FILE, lineNumbers[idx]));
      if (inlineFramesList != null) {
        commands.add(checkInlineFrames(inlineFramesList.get(idx)));
      }
    }
    commands.add(run());
    runDebugTest(config, CLASS_NAME, commands);
  }
}
