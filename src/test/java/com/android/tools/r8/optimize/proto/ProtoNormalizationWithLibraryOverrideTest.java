// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.MethodMatchers.hasParameters;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithLibraryOverrideTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Program.class)
        .addLibraryClasses(A.class, B.class, Library.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with proto changes.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              TypeSubject aTypeSubject = inspector.getTypeSubject(A.class.getTypeName());
              TypeSubject bTypeSubject = inspector.getTypeSubject(B.class.getTypeName());

              MethodSubject fooMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, hasParameters(bTypeSubject, aTypeSubject));

              MethodSubject libraryOverrideMethodSubject =
                  inspector.clazz(Program.class).uniqueMethodWithOriginalName("m");
              assertThat(libraryOverrideMethodSubject, isPresent());
              assertThat(libraryOverrideMethodSubject, hasParameters(bTypeSubject, aTypeSubject));
            })
        .addRunClasspathClasses(A.class, B.class, Library.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Program", "A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      Library library = System.currentTimeMillis() > 0 ? new Program() : new Library();
      library.m(new B(), new A());
      foo(new A(), new B());
    }

    @NeverInline
    static void foo(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }
  }

  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  static class B {

    @Override
    public String toString() {
      return "B";
    }
  }

  public static class Library {

    public void m(B b, A a) {
      System.out.println("Library");
    }
  }

  static class Program extends Library {

    @Override
    public void m(B b, A a) {
      System.out.println("Program");
    }
  }
}
