// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.junit.Test;

public class SimplifyIfNotNullKotlinTest extends AbstractR8KotlinTestBase {
  private static final String FOLDER = "non_null";
  private static final String STRING = "java.lang.String";

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
      long ifzCount = Streams.stream(testMethod.iterateInstructions())
          .filter(i -> i.isIfEqz() || i.isIfNez()).count();
      long paramNullCheckCount =
          countCall(testMethod, "ArrayIteratorKt", "checkParameterIsNotNull");
      if (allowAccessModification) {
        // Three null-check's from inlined checkParameterIsNotNull for receiver and two arguments.
        assertEquals(5, ifzCount);
        assertEquals(0, paramNullCheckCount);
      } else {
        // One after Iterator#hasNext, and another in the filter predicate: sinceYear != null.
        assertEquals(2, ifzCount);
        assertEquals(5, paramNullCheckCount);
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
      long ifzCount = Streams.stream(testMethod.iterateInstructions())
          .filter(InstructionSubject::isIfEqz).count();
      long paramNullCheckCount =
          countCall(testMethod, "Intrinsics", "checkParameterIsNotNull");
      if (allowAccessModification) {
        // One null-check from inlined checkParameterIsNotNull.
        assertEquals(2, ifzCount);
        assertEquals(0, paramNullCheckCount);
      } else {
        // ?: in aOrDefault
        assertEquals(1, ifzCount);
        assertEquals(1, paramNullCheckCount);
      }
    });
  }

  @Test
  public void test_example3() throws Exception {
    final TestKotlinClass ex3 = new TestKotlinClass("non_null.Example3Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("neverThrowNPE", "void", ImmutableList.of("non_null.Foo"));

    final String mainClassName = ex3.getClassName();
    final String extraRules = keepAllMembers(mainClassName);
    runTest(FOLDER, mainClassName, extraRules, app -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject clazz = checkClassIsKept(codeInspector, ex3.getClassName());

      MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
      long ifzCount = Streams.stream(testMethod.iterateInstructions())
          .filter(InstructionSubject::isIfEqz).count();
      // !! operator inside explicit null check should be gone.
      // One explicit null-check as well as 4 bar? accesses.
      assertEquals(5, ifzCount);
    });
  }

}
