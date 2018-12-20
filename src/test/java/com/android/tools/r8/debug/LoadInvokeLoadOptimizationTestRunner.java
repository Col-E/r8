// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.List;
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
  public static List<Object[]> setup() {
    DebugTestParameters parameters =
        parameters()
            .add("CF", temp -> testForJvm(temp).addTestClasspath().debugConfig())
            .add("D8", temp -> testForD8(temp).addProgramClasses(CLASS).debugConfig());
    for (Backend backend : Backend.values()) {
      parameters.add(
          "R8/" + backend,
          temp ->
              testForR8(temp, backend)
                  .noTreeShaking()
                  .noMinification()
                  .addKeepRules("-keepattributes SourceFile,LineNumberTable")
                  .addProgramClasses(CLASS)
                  .setMode(CompilationMode.DEBUG)
                  .debugConfig());
    }
    return parameters.build();
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
