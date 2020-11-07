// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b63935662;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress63935662 extends TestBase {

  private TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public Regress63935662(TestParameters parameters) {
    this.parameters = parameters;
  }

  void run(R8FullTestBuilder testBuilder, Class<?> mainClass) throws Exception {
    testBuilder
        .addKeepRuleFiles()
        .allowAccessModification()
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(runOnJava(mainClass));
  }

  @Test
  public void test() throws Exception {
    Class<?> mainClass = TestClass.class;
    List<Class<?>> app =
        ImmutableList.of(
            TestClass.Top.class,
            TestClass.Left.class,
            TestClass.Right.class,
            TestClass.Bottom.class,
            TestClass.X1.class,
            TestClass.X2.class,
            TestClass.X3.class,
            TestClass.X4.class,
            TestClass.X5.class,
            mainClass);
    run(
        testForR8(parameters.getBackend())
            .addProgramClasses(app)
            .addKeepMainRule(mainClass)
            .enableNoHorizontalClassMergingAnnotations(),
        mainClass);
  }

  @Test
  public void test2() throws Exception {
    Class<?> mainClass = TestFromBug.class;
    List<Class<?>> app =
        ImmutableList.of(
            TestFromBug.Map.class,
            TestFromBug.AbstractMap.class,
            TestFromBug.ConcurrentMap.class,
            TestFromBug.ConcurrentHashMap.class,
            mainClass);
    run(
        testForR8(parameters.getBackend()).addProgramClasses(app).addKeepMainRule(mainClass),
        mainClass);
  }
}
