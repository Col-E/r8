// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontalstatic;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticClassMergerVisibilityTest extends TestBase {

  private final TestParameters parameters;

  public StaticClassMergerVisibilityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testStaticClassIsRemoved() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(StaticClassMergerVisibilityTest.class)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("A.print()", "B.print()", "C.print()", "D.print()")
            .inspector();

    // The global group will have one representative, which is C.
    ClassSubject clazzC = inspector.clazz(C.class);
    assertThat(clazzC, isPresent());
    assertEquals(1, clazzC.allMethods().size());

    // The package group will be merged into one of A, B or C.
    assertExactlyOneIsPresent(
        inspector.clazz(A.class), inspector.clazz(B.class), inspector.clazz(D.class));
  }

  private void assertExactlyOneIsPresent(ClassSubject... subjects) {
    boolean seenPresent = false;
    for (ClassSubject subject : subjects) {
      if (subject.isPresent()) {
        assertFalse(seenPresent);
        seenPresent = true;
      }
    }
    assertTrue(seenPresent);
  }

  // Will be merged into the package group.
  private static class A {
    @NeverInline
    private static void print() {
      System.out.println("A.print()");
    }
  }

  // Will be merged into the package group.
  static class B {
    @NeverInline
    static void print() {
      System.out.println("B.print()");
    }
  }

  // Will be merged into global group
  public static class C {
    @NeverInline
    public static void print() {
      System.out.println("C.print()");
    }
  }

  // Will be merged into the package group.
  protected static class D {
    @NeverInline
    protected static void print() {
      System.out.println("D.print()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.print();
      B.print();
      C.print();
      D.print();
    }
  }
}
