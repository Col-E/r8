// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B77944861 extends TestBase {

  private static final String MAIN = "regress_77944861.SomeView";
  private static final String EXPECTED_OUTPUT = StringUtils.lines("foo");

  private static final Path PRG =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "regress_77944861" + FileUtils.JAR_EXTENSION);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(PRG)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(PRG)
        .addKeepMainRule(MAIN)
        .addDontObfuscate()
        .addDontShrink()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject view = inspector.clazz("regress_77944861.SomeView");
              assertThat(view, isPresent());
              String className = "regress_77944861.inner.TopLevelPolicy$MobileIconState";
              MethodSubject initView =
                  view.method("java.lang.String", "get", ImmutableList.of(className));
              assertThat(initView, isPresent());
              Iterator<InstructionSubject> iterator = initView.iterateInstructions();
              InstructionSubject instruction;
              do {
                assertTrue(iterator.hasNext());
                instruction = iterator.next();
              } while (!instruction.isInstanceGet());

              assertEquals(
                  className, ((FieldAccessInstructionSubject) instruction).holder().toString());

              do {
                assertTrue(iterator.hasNext());
                instruction = iterator.next();
              } while (!instruction.isReturnObject());
            })
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
