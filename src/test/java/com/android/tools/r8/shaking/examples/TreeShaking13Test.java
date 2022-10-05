// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking13Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking13Test(Frontend frontend, TestParameters parameters, MinifyMode minify) {
    super(frontend, parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking13";
  }

  @Override
  protected String getMainClass() {
    return "shaking13.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking13Test::shaking13EnsureFieldWritesCorrect,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking13/keep-rules.txt"));
  }

  private static void shaking13EnsureFieldWritesCorrect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz("shaking13.Shaking");
    MethodSubject testMethod = mainClass.uniqueMethodWithOriginalName("fieldTest");
    Assert.assertTrue(testMethod.isPresent());
    Iterator<FieldAccessInstructionSubject> iterator =
        testMethod.iterateInstructions(InstructionSubject::isFieldAccess);
    Assert.assertTrue(iterator.hasNext() && iterator.next().holder().is("shakinglib.LibraryClass"));
    Assert.assertTrue(iterator.hasNext() && iterator.next().holder().is("shakinglib.LibraryClass"));
    Assert.assertFalse(iterator.hasNext());
  }
}
