// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestCondition.R8_COMPILER;
import static com.android.tools.r8.TestCondition.match;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.R8RunArtTestsTest.DexTool;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunExamplesKotlinTest extends R8RunExamplesCommon {

  @Parameters(name = "{0}_{1}_{2}_{3}_{5}")
  public static Collection<String[]> data() {
    String[] tests = {
        "loops.LoopKt"
    };

    List<String[]> fullTestList = new ArrayList<>(tests.length * 2);
    for (String test : tests) {
      fullTestList.add(makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.DEBUG, test));
      fullTestList.add(makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.RELEASE, test));
      fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.DEBUG, test));
      fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.RELEASE, test));
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

  public R8RunExamplesKotlinTest(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String output) {
    super(pkg, input, compiler, mode, mainClass, output);
  }
}
