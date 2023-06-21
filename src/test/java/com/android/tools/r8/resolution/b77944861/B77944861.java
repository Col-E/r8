// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.b77944861;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.resolution.b77944861.inner.TopLevelPolicy;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B77944861 extends TestBase {

  private static final Class MAIN = SomeView.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("foo");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        MAIN,
        TopLevelPolicy.class,
        TopLevelPolicy.MobileIconState.class,
        Class.forName(typeName(TopLevelPolicy.class) + "$1"),
        Class.forName(typeName(TopLevelPolicy.class) + "$IconState"));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(getTestClasses())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getTestClasses())
        .addKeepMainRule(MAIN)
        .addDontObfuscate()
        .addDontShrink()
        .enableNoAccessModificationAnnotationsForClasses()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject view = inspector.clazz(MAIN);
              assertThat(view, isPresent());
              String className = typeName(TopLevelPolicy.MobileIconState.class);
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
