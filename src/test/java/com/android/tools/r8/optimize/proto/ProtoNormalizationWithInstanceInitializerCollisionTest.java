// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithInstanceInitializerCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              TypeSubject aTypeSubject = aClassSubject.asTypeSubject();
              TypeSubject bTypeSubject = bClassSubject.asTypeSubject();

              // Main.<init>(A, B) is unchanged.
              MethodSubject initMethodSubject =
                  mainClassSubject.initFromTypes(aTypeSubject, bTypeSubject);
              assertThat(initMethodSubject, isPresent());

              // Main.<init>(B, A) is unchanged.
              MethodSubject otherInitMethodSubject =
                  mainClassSubject.initFromTypes(bTypeSubject, aTypeSubject);
              assertThat(otherInitMethodSubject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      new Main(new A(), new B());
      new Main(new B(), new A());
    }

    @NeverInline
    Main(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    Main(B b, A a) {
      System.out.println(a);
      System.out.println(b);
    }
  }

  @NoHorizontalClassMerging
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  @NoHorizontalClassMerging
  static class B {

    @Override
    public String toString() {
      return "B";
    }
  }
}
