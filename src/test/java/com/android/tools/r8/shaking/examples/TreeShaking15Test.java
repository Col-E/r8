// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.FieldSubject;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking15Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      if (minify == MinifyMode.NONE) {
        continue;
      }
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShaking15Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking15", "shaking15.Shaking", frontend, backend, minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking15Test::shaking15testDictionary,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking15/keep-rules.txt"));
  }

  private static void shaking15testDictionary(DexInspector inspector) {
    inspector.forAllClasses((clazz) -> checkClassAndMemberInDictionary(clazz));
  }

  private static List<String> names =
      ImmutableList.of("pqr", "vw$", "abc", "def", "stu", "ghi", "jkl", "ea", "xyz_", "mno");

  private static void checkClassAndMemberInDictionary(ClassSubject clazz) {
    String name = clazz.getDexClass().type.getName();
    if (!names.contains(name) && !name.equals("Shaking")) {
      throw new AssertionError();
    }

    clazz.forAllMethods(TreeShaking15Test::checkMethodInDictionary);
    clazz.forAllFields(TreeShaking15Test::checkFieldInDictionary);
  }

  private static void checkFieldInDictionary(FieldSubject field) {
    if (!names.contains(field.getField().field.name.toSourceString())) {
      throw new AssertionError();
    }
  }

  private static void checkMethodInDictionary(MethodSubject method) {
    String name = method.getMethod().method.name.toSourceString();
    if (!names.contains(name) && !name.equals("<init>") && !name.equals("main")) {
      throw new AssertionError();
    }
  }
}
