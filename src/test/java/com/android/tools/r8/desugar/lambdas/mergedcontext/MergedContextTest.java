// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas.mergedcontext;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MergedContextTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("B::foo", "C::bar");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MergedContextTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class, B.class, C.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules("-repackageclasses \"repackaged\"")
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              inspector.assertClassesNotMerged(C.class);
              inspector.assertMergedInto(B.class, A.class);
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  /* This class will be merged with class B (with A being the result). This class has a package
   * protected access to ensure that it cannot be repackaged. */
  @NeverClassInline
  public static class A {

    @NeverInline
    public void ensureNotRepackaged() {
      TestClass.packageProtectedMethodToDisableRepackage();
    }
  }

  /* This class is merged into A. */
  @NeverClassInline
  public static class B {

    @NeverInline
    public Runnable foo() {
      C c = new C();
      // This synthetic lambda class uses package protected access to C. Its context will initially
      // be B, thus the synthetic will internally be B-$$Synthetic. The lambda can be repackaged
      // together with the accessed class C. However, once A and B are merged as A, the context
      // implicitly changes. If repackaging does not either see or adjust the context, the result
      // will be that the external synthetic lambda will become A-$$Synthetic,
      // with the consequence that the call to repackaged.C.protectedMethod() will throw IAE.
      return () -> {
        System.out.println("B::foo");
        c.packageProtectedMethod();
      };
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class C {

    @NeverInline
    void packageProtectedMethod() {
      System.out.println("C::bar");
    }
  }

  static class TestClass {

    static void packageProtectedMethodToDisableRepackage() {
      if (System.nanoTime() < 0) {
        throw new RuntimeException();
      }
    }

    public static void main(String[] args) {
      new A().ensureNotRepackaged();
      new B().foo().run();
    }
  }
}
