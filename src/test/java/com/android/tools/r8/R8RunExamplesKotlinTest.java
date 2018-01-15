// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
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

  @Parameters(name = "{0}_{1}_{2}_{3}_{5}_{6}")
  public static Collection<Object[]> data() {
    String[] tests = {
        "loops.LoopKt"
    };

    final int arrayListSize = tests.length *
        CompilationMode.values().length *
        KotlinTargetVersion.values().length *
        2 /* JAVAC+D8 and DX+R8 */;
    List<Object[]> fullTestList = new ArrayList<>(arrayListSize);
    for (String test : tests) {
      for (CompilationMode compilationMode : CompilationMode.values()) {
        for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
          fullTestList.add(
              makeTest(Input.JAVAC, CompilerUnderTest.D8, compilationMode, targetVersion, test));
          fullTestList
              .add(makeTest(Input.DX, CompilerUnderTest.R8, compilationMode, targetVersion, test));
        }
      }
    }
    return fullTestList;
  }

  private static Object[] makeTest(Input input, CompilerUnderTest compiler, CompilationMode mode,
      KotlinTargetVersion targetVersion, String clazz) {
    String[] testParams = makeTest(input, compiler, mode, clazz);
    Object[] kotlinTestParams = new Object[testParams.length + 1];
    System.arraycopy(testParams, 0, kotlinTestParams, 0, testParams.length);
    kotlinTestParams[testParams.length] = targetVersion;
    return kotlinTestParams;
  }

  @Override
  protected String getExampleDir() {
    return ToolHelper.getKotlinExamplesBuildDir(targetVersion);
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

  @Override
  protected Map<String, TestCondition> getSkip() {
    return Collections.emptyMap();
  }

  private final KotlinTargetVersion targetVersion;

  public R8RunExamplesKotlinTest(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String output,
      KotlinTargetVersion targetVersion
      ) {
    super(pkg, input, compiler, mode, mainClass, output);
    this.targetVersion = targetVersion;
  }
}
