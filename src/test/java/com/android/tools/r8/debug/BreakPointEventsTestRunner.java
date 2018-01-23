// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests local variable information. */
@RunWith(Parameterized.class)
public class BreakPointEventsTestRunner extends DebugTestBase {

  public static final Class<BreakPointEventsTest> CLASS = BreakPointEventsTest.class;
  public static final String FILE = CLASS.getSimpleName() + ".java";

  private static Path getClassFilePath() {
    return ToolHelper.getClassFileForTestClass(CLASS);
  }

  public static DebugTestConfig cfConfig() throws Exception {
    return new CfDebugTestConfig(ToolHelper.getClassPathForTests());
  }

  public static DebugTestConfig d8Config() throws Exception {
    return new D8DebugTestConfig().compileAndAdd(temp, getClassFilePath());
  }

  public static DebugTestConfig dxConfig() throws Exception {
    Path cwd = ToolHelper.getClassPathForTests().toAbsolutePath();
    Path test = cwd.relativize(getClassFilePath().toAbsolutePath());
    Path out = temp.newFolder().toPath().resolve("classes.dex").toAbsolutePath();
    ProcessResult result = ToolHelper.runDX(cwd, "--output=" + out, test.toString());
    assertTrue(result.stderr, 0 == result.exitCode);
    DebugTestConfig config = new DexDebugTestConfig();
    config.addPaths(out);
    return config;
  }

  public void printConfig(String name, DebugTestConfig config) throws Exception {
    new DebugStreamComparator()
        .add(name, streamDebugTest(config, CLASS.getCanonicalName(), NO_FILTER))
        .setPrintStates(true)
        .setPrintMethod(true)
        .setPrintVariables(true)
        .setFilter(s -> s.getSourceFile().equals(FILE))
        .run();
  }

  // The main method will single-step each configuration of the test. It cannot be made a test as
  // the event streams are unequal due to ART vs JVM line change on return and DX incorrect locals.
  public static void main(String[] args) throws Exception {
    temp.create();
    try {
      BreakPointEventsTestRunner runner = new BreakPointEventsTestRunner("unused name");
      System.out.println("\n============== CF single stepping: ");
      runner.printConfig("CF", cfConfig());
      System.out.println("\n============== D8 single stepping: ");
      runner.printConfig("D8", d8Config());
      System.out.println("\n============== DX single stepping: ");
      runner.printConfig("DX", dxConfig());
    } finally {
      temp.delete();
    }
  }

  @Parameters(name = "{0}")
  public static Collection<String> configs() {
    return Arrays.asList("CF", "D8");
  }

  private DebugTestConfig config;

  public BreakPointEventsTestRunner(String name) throws Exception {
    config = name.equals("CF") ? cfConfig() : name.equals("D8") ? d8Config() : null;
  }

  @Test
  public void testSingleLineDeclarations() throws Throwable {
    int bLine = 11;
    Value bValueOne = Value.createInt(1);
    Value bValueTwo = Value.createInt(2);
    runDebugTest(
        config,
        CLASS.getCanonicalName(),
        // Install breakpoint on b which is hit 6 times during execution.
        breakpoint(CLASS.getCanonicalName(), "b"),
        // First set of declarations will have respectively {}, {x}, and {x,y} visible in the frame.
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              frame.checkNoLocal("x");
              frame.checkNoLocal("y");
              frame.checkNoLocal("z");
            }),
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              frame.checkLocal("x", bValueOne);
              frame.checkNoLocal("y");
              frame.checkNoLocal("z");
            }),
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              frame.checkLocal("x", bValueOne);
              frame.checkLocal("y", bValueOne);
              frame.checkNoLocal("z");
            }),
        // Second set of declarations should appear the same, only with another value.
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              // This fails on DX compiled output since x will appear visible here.
              frame.checkNoLocal("x");
              frame.checkNoLocal("y");
              frame.checkNoLocal("z");
            }),
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              frame.checkLocal("x", bValueTwo);
              frame.checkNoLocal("y");
              frame.checkNoLocal("z");
            }),
        run(),
        checkLine(FILE, bLine),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(1);
              frame.checkLocal("x", bValueTwo);
              frame.checkLocal("y", bValueTwo);
              frame.checkNoLocal("z");
            }),
        // Install additonal breakpoint on return to main so we identify program execution ended.
        breakpoint(CLASS.getCanonicalName(), "main", 22),
        run(),
        checkLine(FILE, 22),
        run());
  }
}
