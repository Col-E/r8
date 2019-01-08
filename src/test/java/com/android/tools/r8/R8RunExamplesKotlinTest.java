// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunExamplesKotlinTest extends R8RunExamplesCommon {

  @Override
  protected void configure(InternalOptions options) {
    options.enableCfFrontend = frontend == Frontend.CF;
    if (output == Output.CF) {
      // Class inliner is not supported with CF backend yet.
      options.enableClassInlining = false;
    }
  }

  @Parameters(name = "{0}_{1}_{2}_{3}_{5}_{6}")
  public static Collection<String[]> data() {
    String[] tests = {
        "loops.LoopKt"
    };

    List<String[]> fullTestList = new ArrayList<>(tests.length * 2);
    for (String test : tests) {
      fullTestList.add(
          makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.DEBUG, test, Output.DEX));
      fullTestList.add(
          makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.RELEASE, test, Output.DEX));
      fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.DEBUG, test));
      fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.RELEASE, test));
      fullTestList.add(
          makeTest(Input.JAVAC, CompilerUnderTest.R8, CompilationMode.DEBUG, test, Output.CF));
      fullTestList.add(
          makeTest(Input.JAVAC, CompilerUnderTest.R8, CompilationMode.RELEASE, test, Output.CF));
    }
    return fullTestList;
  }

  @Override
  protected String getExampleDir() {
    return ToolHelper.EXAMPLES_KOTLIN_BUILD_DIR;
  }

  @Override
  protected Map<String, TestCondition> getFailingRun() {
    return Collections.emptyMap();
  }

  @Override
  protected Map<String, TestCondition> getFailingRunCf() {
    return Collections.emptyMap();
  }

  @Override
  protected Set<String> getFailingCompileCfToDex() {
    return Collections.emptySet();
  }

  @Override
  protected Set<String> getFailingRunCfToDex() {
    return Collections.emptySet();
  }

  @Override
  protected Set<String> getFailingCompileCf() {
    return Collections.emptySet();
  }

  @Override
  protected Set<String> getFailingOutputCf() {
    return Collections.emptySet();
  }

  @Override
  protected Map<String, TestCondition> getOutputNotIdenticalToJVMOutput() {
    return Collections.emptyMap();
  }

  @Override
  protected Map<String, TestCondition> getSkip() {
    return Collections.emptyMap();
  }

  public R8RunExamplesKotlinTest(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String frontend,
      String output) {
    super(pkg, input, compiler, mode, mainClass, frontend, output);
  }
}
