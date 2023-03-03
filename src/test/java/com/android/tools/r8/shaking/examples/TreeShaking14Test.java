// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking14Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking14Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking14";
  }

  @Override
  protected String getMainClass() {
    return "shaking14.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking14Test::shaking14EnsureRightStaticMethodsLive,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking14/keep-rules.txt"));
  }

  private static void shaking14EnsureRightStaticMethodsLive(CodeInspector inspector) {
    ClassSubject superclass = inspector.clazz("shaking14.Superclass");
    Assert.assertFalse(superclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertFalse(
        superclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
    ClassSubject subclass = inspector.clazz("shaking14.Subclass");
    Assert.assertTrue(subclass.method("int", "aMethod", ImmutableList.of("int")).isPresent());
    Assert.assertTrue(
        subclass.method("double", "anotherMethod", ImmutableList.of("double")).isPresent());
  }
}
