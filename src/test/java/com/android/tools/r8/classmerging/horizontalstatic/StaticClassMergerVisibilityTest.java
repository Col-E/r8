// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontalstatic;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
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
    testForR8(parameters.getBackend())
        .addInnerClasses(StaticClassMergerVisibilityTest.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(A.class, B.class, C.class, D.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.print()", "B.print()", "C.print()", "D.print()")
        .inspect(
            inspector -> {
              // All classes are merged into C.
              ClassSubject classSubject = inspector.clazz(C.class);
              assertThat(classSubject, isPresent());
              assertThat(classSubject, isPublic());
              // A now has 5 methods (there is a synthetic access bridge for the private A.print()).
              assertEquals(5, classSubject.allMethods().size());
            });
  }

  // Will be merged into C.
  private static class A {

    @NeverInline
    @NoAccessModification
    private static void print() {
      System.out.println("A.print()");
    }
  }

  // Will be merged into C.
  static class B {
    @NeverInline
    static void print() {
      System.out.println("B.print()");
    }
  }

  public static class C {
    @NeverInline
    public static void print() {
      System.out.println("C.print()");
    }
  }

  // Will be merged into C.
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
