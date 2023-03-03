// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class TreeShaking8Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking8Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking8";
  }

  @Override
  protected String getMainClass() {
    return "shaking8.Shaking";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        TreeShaking8Test::shaking8ThingClassIsAbstractAndEmpty,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking8/keep-rules.txt"));
  }

  @Test
  public void testKeeprulesprintusage() throws Exception {
    runTest(
        null, null, null, ImmutableList.of("src/test/examples/shaking8/keep-rules-printusage.txt"));
  }

  private static void shaking8ThingClassIsAbstractAndEmpty(CodeInspector inspector) {
    ClassSubject thingClass = inspector.clazz("shaking8.Thing");
    Assert.assertTrue(thingClass.isAbstract());
    thingClass.forAllMethods((method) -> Assert.fail());
    ClassSubject yetAnotherThingClass = inspector.clazz("shaking8.YetAnotherThing");
    assertThat(yetAnotherThingClass, not(isPresent()));
  }
}
