// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.code.Format21t;
import com.android.tools.r8.code.Format22t;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;

public class SimplifyIfNotNullKotlinTest extends AbstractR8KotlinTestBase {
  private static final String FOLDER = "non_null";
  private static final String STRING = "java.lang.String";

  private static boolean isIf(Instruction instruction) {
    return instruction instanceof Format21t || instruction instanceof Format22t;
  }

  @Test
  public void test_example1() throws Exception {
    final TestKotlinClass ex1 = new TestKotlinClass("non_null.Example1Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("forMakeAndModel", "java.util.SortedMap",
            ImmutableList.of("java.util.Collection", STRING, STRING, "java.lang.Integer"));

    final String mainClassName = ex1.getClassName();
    final String extraRules = keepAllMembers(mainClassName);
    runTest(FOLDER, mainClassName, extraRules, app -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject clazz = checkClassIsKept(codeInspector, ex1.getClassName());

      MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
      DexCode dexCode = getDexCode(testMethod);
      long count = Arrays.stream(dexCode.instructions)
          .filter(SimplifyIfNotNullKotlinTest::isIf).count();
      if (allowAccessModification) {
        // Three null-check's from inlined checkParameterIsNotNull for receiver and two arguments.
        assertEquals(5, count);
      } else {
        // One after Iterator#hasNext, and another in the filter predicate: sinceYear != null.
        assertEquals(2, count);
      }
    });
  }

  @Test
  public void test_example2() throws Exception {
    final TestKotlinClass ex2 = new TestKotlinClass("non_null.Example2Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("aOrDefault", STRING, ImmutableList.of(STRING, STRING));

    final String mainClassName = ex2.getClassName();
    final String extraRules = keepAllMembers(mainClassName);
    runTest(FOLDER, mainClassName, extraRules, app -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject clazz = checkClassIsKept(codeInspector, ex2.getClassName());

      MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
      DexCode dexCode = getDexCode(testMethod);
      long count = Arrays.stream(dexCode.instructions)
          .filter(SimplifyIfNotNullKotlinTest::isIf).count();
      // One null-check from force inlined coalesce and another from ?:
      assertEquals(2, count);
    });
  }

}
