// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking12Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
            .build(),
        MinifyMode.values());
  }

  public TreeShaking12Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking12";
  }

  @Override
  protected String getMainClass() {
    return "shaking12.Shaking";
  }

  @Test
  public void testKeeprules() throws Exception {
    runTest(
        TreeShaking12Test::shaking12OnlyInstantiatedClassesHaveConstructors,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking12/keep-rules.txt"));
  }

  @Test
  public void testKeeprulesprintusage() throws Exception {
    runTest(
        null,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking12/keep-rules-printusage.txt"));
  }

  private static void shaking12OnlyInstantiatedClassesHaveConstructors(CodeInspector inspector) {
    // Since AnimalClass is never instantiated, the instruction "item instanceof AnimalClass" should
    // be rewritten into the constant false. After this optimization, AnimalClass is removed because
    // it is no longer reference.
    ClassSubject animalClass = inspector.clazz("shaking12.AnimalClass");
    assertThat(animalClass, not(isPresent()));
    Assert.assertFalse(animalClass.method("void", "<init>", Collections.emptyList()).isPresent());
    Assert.assertTrue(inspector.clazz("shaking12.MetaphorClass").isAbstract());
    ClassSubject peopleClass = inspector.clazz("shaking12.PeopleClass");
    Assert.assertTrue((peopleClass.isPresent() && !peopleClass.isAbstract()));
    Assert.assertTrue(peopleClass.method("void", "<init>", Collections.emptyList()).isPresent());
    ClassSubject thingClass = inspector.clazz("shaking12.ThingClass");
    Assert.assertTrue((thingClass.isPresent() && !thingClass.isAbstract()));
    Assert.assertTrue(thingClass.method("void", "<init>", Collections.emptyList()).isPresent());
  }
}
