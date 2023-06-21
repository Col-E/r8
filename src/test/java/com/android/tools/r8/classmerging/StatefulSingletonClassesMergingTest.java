// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StatefulSingletonClassesMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StatefulSingletonClassesMergingTest(TestParameters parameters) {
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
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  static class Main {
    public static void main(String[] args) {
      A.INSTANCE.f();
      B.INSTANCE.g();
    }
  }

  @NeverClassInline
  static class A {

    static final A INSTANCE = new A("A");

    @NeverPropagateValue @NoAccessModification private final String data;

    // TODO(b/198758663): With argument propagation the constructors end up not being equivalent,
    //  which prevents merging in the final round of horizontal class merging.
    @KeepConstantArguments
    A(String data) {
      this.data = data;
    }

    @NeverInline
    void f() {
      System.out.println(data);
    }
  }

  @NeverClassInline
  static class B {

    static final B INSTANCE = new B("B");

    @NeverPropagateValue @NoAccessModification private String data;

    // TODO(b/198758663): With argument propagation the constructors end up not being equivalent,
    //  which prevents merging in the final round of horizontal class merging.
    @KeepConstantArguments
    B(String data) {
      this.data = data;
    }

    @NeverInline
    void g() {
      System.out.println(data);
    }
  }
}
