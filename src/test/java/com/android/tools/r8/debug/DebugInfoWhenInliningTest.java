// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;


import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestConfig.RuntimeKind;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests source file and line numbers on inlined methods. */
@RunWith(Parameterized.class)
public class DebugInfoWhenInliningTest extends DebugTestBase {

  public enum Config {
    CF,
    DEX_NO_FORCE_JUMBO,
    DEX_FORCE_JUMBO
  };

  private static final String SOURCE_FILE = "Inlining1.java";

  private DebugTestConfig makeConfig(
      LineNumberOptimization lineNumberOptimization,
      boolean writeProguardMap,
      RuntimeKind runtimeKind)
      throws Exception {
    DebugTestConfig config = null;
    Path outdir = temp.newFolder().toPath();
    Path outjar = outdir.resolve("r8_compiled.jar");
    R8Command.Builder builder =
        R8Command.builder().addProgramFiles(DEBUGGEE_JAR).setMode(CompilationMode.RELEASE);

    if (runtimeKind == RuntimeKind.DEX) {
      AndroidApiLevel minSdk = ToolHelper.getMinApiLevelForDexVm();
      builder
          .setMinApiLevel(minSdk.getLevel())
          .addLibraryFiles(ToolHelper.getAndroidJar(minSdk))
          .setOutput(outjar, OutputMode.DexIndexed);
      config = new DexDebugTestConfig(outjar);
    } else {
      assert (runtimeKind == RuntimeKind.CF);
      builder
          .setOutput(outjar, OutputMode.ClassFile)
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
      config = new CfDebugTestConfig(outjar);
    }

    if (writeProguardMap) {
      Path proguardMapPath = outdir.resolve("proguard.map");
      builder.setProguardMapOutputPath(proguardMapPath);
      config.setProguardMap(proguardMapPath);
    }

    ToolHelper.runR8(
        builder.build(), options -> {
          options.lineNumberOptimization = lineNumberOptimization;
          options.testing.forceJumboStringProcessing = forceJumboStringProcessing;
        });

    return config;
  }

  private boolean forceJumboStringProcessing;
  private RuntimeKind runtimeKind;

  @Parameters(name = "config: {0}")
  public static Collection<Config> data() {
    return Arrays.asList(Config.values());
  }

  public DebugInfoWhenInliningTest(Config config) {
    this.forceJumboStringProcessing = config == Config.DEX_FORCE_JUMBO;
    this.runtimeKind = config == Config.CF ? RuntimeKind.CF : RuntimeKind.DEX;
  }

  @Test
  public void testEachLineNotOptimized() throws Throwable {
    // The reason why the not-optimized test contains half as many line numbers as the optimized
    // one:
    //
    // In the Java source (Inlining1) each call is duplicated. Since they end up on the same line
    // (innermost callee) the line numbers are actually 7, 7, 32, 32, ... but even if the positions
    // are emitted duplicated in the dex code, the debugger stops only when there's a change.
    int[] lineNumbers = {7, 32, 11, 7};
    testEachLine(makeConfig(LineNumberOptimization.OFF, false, runtimeKind), lineNumbers);
  }

  @Test
  public void testEachLineNotOptimizedWithMap() throws Throwable {
    // The reason why the not-optimized test contains half as many line numbers as the optimized
    // one:
    //
    // In the Java source (Inlining1) each call is duplicated. Since they end up on the same line
    // (innermost callee) the line numbers are actually 7, 7, 32, 32, ... but even if the positions
    // are emitted duplicated in the dex code, the debugger stops only when there's a change.
    int[] lineNumbers = {7, 32, 11, 7};
    testEachLine(makeConfig(LineNumberOptimization.OFF, true, runtimeKind), lineNumbers);
  }

  @Test
  public void testEachLineOptimized() throws Throwable {
    int[] lineNumbers = {1, 2, 3, 4, 5, 6, 7, 8};
    testEachLine(makeConfig(LineNumberOptimization.ON, false, runtimeKind), lineNumbers);
  }

  @Test
  public void testEachLineOptimizedWithMap() throws Throwable {
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
    testEachLine(
        makeConfig(LineNumberOptimization.ON, true, runtimeKind), lineNumbers, inlineFramesList);
  }

  private void testEachLine(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    testEachLine(config, lineNumbers, null);
  }

  private void testEachLine(
      DebugTestConfig config, int[] lineNumbers, List<List<SignatureAndLine>> inlineFramesList)
      throws Throwable {
    final String className = "Inlining1";
    final String mainSignature = "([Ljava/lang/String;)V";

    List<Command> commands = new ArrayList<Command>();
    commands.add(breakpoint(className, "main", mainSignature));
    commands.add(run());
    boolean first = true;
    assert inlineFramesList == null || inlineFramesList.size() == lineNumbers.length;
    for (int idx = 0; idx < lineNumbers.length; ++idx) {
      if (first) {
        first = false;
      } else {
        commands.add(stepOver());
      }
      commands.add(checkMethod(className, "main", mainSignature));
      commands.add(checkLine(SOURCE_FILE, lineNumbers[idx]));
      if (inlineFramesList != null) {
        commands.add(checkInlineFrames(inlineFramesList.get(idx)));
      }
    }
    commands.add(run());
    runDebugTest(config, className, commands);
  }
}
