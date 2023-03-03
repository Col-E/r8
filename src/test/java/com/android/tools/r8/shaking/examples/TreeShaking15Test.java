// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking15Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return data(MinifyMode.withoutNone());
  }

  public TreeShaking15Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking15";
  }

  @Override
  protected String getMainClass() {
    return "shaking15.Shaking";
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking15Test::shaking15testDictionary,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking15/keep-rules.txt"),
        null,
        R8TestBuilder::allowDiagnosticInfoMessages,
        diagnostics ->
            diagnostics.assertAllInfosMatch(
                diagnosticMessage(containsString("Invalid character in dictionary"))));
  }

  private static void shaking15testDictionary(CodeInspector inspector) {
    inspector.forAllClasses(TreeShaking15Test::checkClassAndMemberInDictionary);
  }

  private static List<String> names =
      ImmutableList.of("pqr", "vw$", "abc", "def", "stu", "ghi", "jkl", "ea", "xyz_", "mno");

  private static void checkClassAndMemberInDictionary(ClassSubject clazz) {
    String name = clazz.getDexProgramClass().type.getName();
    if (!names.contains(name) && !name.equals("Shaking")) {
      throw new AssertionError();
    }

    clazz.forAllMethods(TreeShaking15Test::checkMethodInDictionary);
    clazz.forAllFields(TreeShaking15Test::checkFieldInDictionary);
  }

  private static void checkFieldInDictionary(FieldSubject field) {
    if (!names.contains(field.getField().getReference().name.toSourceString())) {
      throw new AssertionError();
    }
  }

  private static void checkMethodInDictionary(MethodSubject method) {
    String name = method.getMethod().getReference().name.toSourceString();
    if (!names.contains(name) && !name.equals("<init>") && !name.equals("main")) {
      throw new AssertionError();
    }
  }
}
