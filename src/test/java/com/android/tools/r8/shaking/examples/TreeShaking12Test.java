// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking12Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShaking12Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking12", "shaking12.Shaking", frontend, backend, minify);
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

  private static void shaking12OnlyInstantiatedClassesHaveConstructors(DexInspector inspector) {
    ClassSubject animalClass = inspector.clazz("shaking12.AnimalClass");
    Assert.assertTrue(animalClass.isPresent());
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
