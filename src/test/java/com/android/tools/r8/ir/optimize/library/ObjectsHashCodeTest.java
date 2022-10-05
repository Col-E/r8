// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsHashCodeTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ObjectsHashCodeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testNonNullArgumentMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNonNullArgument");
              assertThat(testNonNullArgumentMethodSubject, isPresent());
              assertThat(
                  testNonNullArgumentMethodSubject,
                  not(invokesMethodWithHolderAndName("java.util.Objects", "hashCode")));

              MethodSubject testNullArgumentMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNullArgument");
              assertThat(testNullArgumentMethodSubject, isPresent());
              assertThat(testNullArgumentMethodSubject, not(invokesMethodWithName("hashCode")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "0");
  }

  static class Main {

    public static void main(String[] args) {
      testNonNullArgument();
      testNullArgument();
    }

    @NeverInline
    static void testNonNullArgument() {
      System.out.println(Objects.hashCode(new A()));
    }

    @NeverInline
    static void testNullArgument() {
      System.out.println(Objects.hashCode(null));
    }
  }

  @NeverClassInline
  static class A {

    @Override
    public int hashCode() {
      return System.currentTimeMillis() > 0 ? 42 : -1;
    }
  }
}
