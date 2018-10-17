// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LoadInvokeLoadOptimizationTestRunner extends DebugTestBase {

  static final Class CLASS = LoadInvokeLoadOptimizationTest.class;
  static final String NAME = CLASS.getCanonicalName();
  static final String DESC = DescriptorUtils.javaTypeToDescriptor(NAME);
  static final String FILE = CLASS.getSimpleName() + ".java";

  private final String name;
  private final DebugTestConfig config;

  @Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
        temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DelayedDebugTestConfig r8cf =
        temp -> {
          Path out = null;
          try {
            out = temp.newFolder().toPath().resolve("out.jar");
            R8.run(
                R8Command.builder()
                    .addProgramFiles(ToolHelper.getClassFileForTestClass(CLASS))
                    .addLibraryFiles(runtimeJar(Backend.CF))
                    .setMode(CompilationMode.DEBUG)
                    .setOutput(out, OutputMode.ClassFile)
                    .build());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return new CfDebugTestConfig().addPaths(out);
        };
    DelayedDebugTestConfig d8 =
        temp ->
            new D8DebugTestConfig().compileAndAdd(temp, ToolHelper.getClassFileForTestClass(CLASS));
    return ImmutableList.of(
        new Object[] {"CF", cf}, new Object[] {"R8CF", r8cf}, new Object[] {"D8", d8});
  }

  public LoadInvokeLoadOptimizationTestRunner(String name, DelayedDebugTestConfig config) {
    this.name = name;
    this.config = config.getConfig(temp);
  }

  @Test
  public void test() throws Throwable {
    Value int42 = Value.createInt(42);
    Value int7 = Value.createInt(7);
    // The test ensures that when breaking inside a function and changing a local in the parent
    // frame, that the new value is passed to the second invocation of the function.
    // This ensures that no peephole optimizations will optimize if there is any debug information.
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "bar"),
        run(),
        checkLine(FILE, 10),
        checkLocal("x", int42),
        inspect(
            t -> {
              FrameInspector frame = t.getFrame(1);
              frame.checkLine(FILE, 13);
              frame.checkLocal("x", int42);
              frame.setLocal("x", int7);
            }),
        run(),
        checkLine(FILE, 10),
        checkLocal("x", int7),
        run());
  }
}
