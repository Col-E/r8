// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class MergingWithSafeCheckCastTest extends HorizontalClassMergingTestBase {

  public MergingWithSafeCheckCastTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(A.class, B.class)
                    .assertIsCompleteMergeGroup(I.class, J.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              // Check that the field f has been changed to have type java.lang.Object.
              assertEquals(1, aClassSubject.allFields().size());
              assertEquals(
                  Object.class.getTypeName(),
                  aClassSubject.allFields().get(0).getField().getType().getTypeName());

              // Check that casts have been inserted into main().
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertTrue(
                  mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isCheckCast));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "J");
  }

  public static class Main {
    public static void main(String[] args) {
      new A(new IImpl()).f.printI();
      new B(new JImpl()).f.printJ();
    }
  }

  @NeverClassInline
  public static class A {

    @NeverPropagateValue @NoAccessModification I f;

    A(I f) {
      this.f = f;
    }
  }

  @NeverClassInline
  public static class B {

    @NeverPropagateValue @NoAccessModification J f;

    B(J f) {
      this.f = f;
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {
    void printI();
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J {
    void printJ();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class IImpl implements I {
    @NeverInline
    public void printI() {
      System.out.println("I");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class JImpl implements J {
    @NeverInline
    public void printJ() {
      System.out.println("J");
    }
  }
}
