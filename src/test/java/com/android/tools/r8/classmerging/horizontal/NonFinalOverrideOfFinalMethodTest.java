// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NonFinalOverrideOfFinalMethodTest extends HorizontalClassMergingTestBase {

  @Parameterized.Parameters(name = "{0}, horizontalClassMerging:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.trueValues());
  }

  public NonFinalOverrideOfFinalMethodTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.horizontalClassMergerOptions().enableIf(enableHorizontalClassMerging))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertMergedInto(B.class, A.class))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject synchronizedMethodSubject = aClassSubject.uniqueMethodWithName("foo");
              assertThat(synchronizedMethodSubject, isPresent());
              assertFalse(synchronizedMethodSubject.isFinal());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "BSub");
  }

  public static class Main {
    public static void main(String[] args) {
      new A().foo();
      new B().bar();
      new BSub().foo();
    }
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    public final void foo() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class B {

    @NeverInline
    public void bar() {
      System.out.println("B");
    }
  }

  @NeverClassInline
  public static class BSub extends B {

    @NeverInline
    public void foo() {
      System.out.println("BSub");
    }
  }
}
