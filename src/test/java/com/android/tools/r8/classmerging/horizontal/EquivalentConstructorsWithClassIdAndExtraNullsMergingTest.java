// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EquivalentConstructorsWithClassIdAndExtraNullsMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EquivalentConstructorsWithClassIdAndExtraNullsMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals(
                  2, aClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());
              assertThat(
                  aClassSubject.method("void", "<init>", "java.lang.Object", "int"), isPresent());
              assertThat(
                  aClassSubject.method("void", "<init>", "java.lang.Object", "int", "int"),
                  isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C", "0", "C", "D");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new A(new C()));
      System.out.println(new A(new C(), args.length));
      System.out.println(new B(new D()));
    }
  }

  @NeverClassInline
  static class A {

    @NoAccessModification private final Object c;

    A(C c) {
      this.c = c;
    }

    A(Object c, int i) {
      this.c = c;
      System.out.println(i);
    }

    @Override
    public String toString() {
      return c.toString();
    }
  }

  @NeverClassInline
  static class B {

    @NoAccessModification private final D d;

    B(D d) {
      this.d = d;
    }

    @Override
    public String toString() {
      return d.toString();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class C {

    @Override
    public String toString() {
      return "C";
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class D {

    @Override
    public String toString() {
      return "D";
    }
  }
}
