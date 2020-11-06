// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateAllowAccessModificationPackageTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"B::foo", "ASub::foo", "A::foo"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateAllowAccessModificationPackageTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, ASub.class, B.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, ASub.class, B.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(A.class)
        .allowAccessModification()
        .enableNoHorizontalClassMergingAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            options -> {
              options.enablePackagePrivateAwarePublicization = true;
            })
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(ASub.class);
              assertThat(clazz, isPresentAndRenamed());
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/172496438): This should not fail.
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    void foo() {
      System.out.println("A::foo");
    }
  }

  public static class ASub extends A {

    @Override
    void foo() {
      System.out.println("ASub::foo");
      super.foo();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class B {

    @NeverInline
    void foo() {
      System.out.println("B::foo");
      ((A) new ASub()).foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().foo();
    }
  }
}
