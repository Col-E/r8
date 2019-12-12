// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryOverrideInliningTest extends TestBase {

  private final boolean disableInliningOfLibraryMethodOverrides;
  private final TestParameters parameters;

  @Parameters(name = "{1}, disableInliningOfLibraryMethodOverrides: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public LibraryOverrideInliningTest(
      boolean disableInliningOfLibraryMethodOverrides, TestParameters parameters) {
    this.disableInliningOfLibraryMethodOverrides = disableInliningOfLibraryMethodOverrides;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LibraryOverrideInliningTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options ->
                options.disableInliningOfLibraryMethodOverrides =
                    disableInliningOfLibraryMethodOverrides)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject greeterClassSubject = inspector.clazz(Greeter.class);
              assertThat(greeterClassSubject, isPresent());

              MethodSubject toStringMethodSubject =
                  greeterClassSubject.uniqueMethodWithName("toString");
              assertThat(toStringMethodSubject, isPresent());

              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              assertThat(testClassSubject, isPresent());

              MethodSubject mainMethodSubject = testClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());

              if (disableInliningOfLibraryMethodOverrides) {
                assertThat(mainMethodSubject, invokesMethod(toStringMethodSubject));
              } else {
                assertThat(mainMethodSubject, not(invokesMethod(toStringMethodSubject)));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Greeter().toString());
    }
  }

  @NeverClassInline
  static class Greeter {

    final char[] greeting = new char["Hello world!".length()];

    // A method that returns "Hello world!" and is not a 'SIMPLE' inlining candidate.
    @Override
    public String toString() {
      String greeting = "Hello world!";
      for (int i = 0; i < greeting.length(); i++) {
        this.greeting[i] = greeting.charAt(i);
      }
      StringBuilder result = new StringBuilder();
      for (char c : greeting.toCharArray()) {
        result.append(c);
      }
      return result.toString();
    }
  }
}
