// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsRequireNonNullElseTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ObjectsRequireNonNullElseTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Expected NPE");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
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
                  not(invokesMethodWithName("requireNonNullElse")));

              MethodSubject testNullArgumentMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNullArgument");
              assertThat(testNullArgumentMethodSubject, isPresent());
              assertThat(
                  testNullArgumentMethodSubject, not(invokesMethodWithName("requireNonNullElse")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo", "Bar", "Expected NPE");
  }

  private byte[] getProgramClassFileData() throws IOException {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Mock.class), descriptor(Objects.class))
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      testNonNullArgument();
      testNullArgument();
      try {
        testNullArgumentAndNullDefaultValue();
        System.out.println("Unexpected");
      } catch (NullPointerException e) {
        System.out.println("Expected NPE");
      }
    }

    @NeverInline
    static void testNonNullArgument() {
      System.out.println(Mock.requireNonNullElse("Foo", ":-("));
    }

    @NeverInline
    static void testNullArgument() {
      System.out.println(Mock.requireNonNullElse(null, "Bar"));
    }

    @NeverInline
    static void testNullArgumentAndNullDefaultValue() {
      System.out.println(Mock.requireNonNullElse(null, null));
    }
  }

  // References to this class are rewritten to java.util.Objects by transformation.
  static class Mock {

    public static Object requireNonNullElse(Object obj, Object defaultObj) {
      throw new RuntimeException();
    }
  }
}
