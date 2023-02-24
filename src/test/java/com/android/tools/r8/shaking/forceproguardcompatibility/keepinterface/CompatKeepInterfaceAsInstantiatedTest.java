// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility.keepinterface;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompatKeepInterfaceAsInstantiatedTest extends TestBase {

  public static class TestCast {

    public static void main(String[] args) throws Exception {
      Object bar =
          Class.forName(
                  TestCast.class.getPackage().getName()
                      + ".CompatKeepInterfaceAsInstantiatedTest$Ba"
                      + (args.length == 42 ? "" : "r"))
              .getDeclaredConstructor()
              .newInstance();
      System.out.println(
          "It was a Foo! "
              // This cast should trigger a compatibility keep for Foo.
              + ((Foo) bar).getClass().getName());
    }
  }

  public static class TestInstanceOf {

    public static void main(String[] args) throws Exception {
      Object bar =
          Class.forName(
                  TestInstanceOf.class.getPackage().getName()
                      + ".CompatKeepInterfaceAsInstantiatedTest$Ba"
                      + (args.length == 42 ? "" : "r"))
              .getDeclaredConstructor()
              .newInstance();
      // This instance-of causes Foo to be kept.
      if (bar instanceof Foo) {
        System.out.println("It was a Foo! " + bar.getClass().getName());
      } else {
        System.out.println("Who knows what it is...");
      }
    }
  }

  // Foo interface referenced in the above test classes.
  public interface Foo {}

  // Bar implementation of Foo, but not visible at compile time, reflectively created.
  public static class Bar implements Foo {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private static final String expected =
      StringUtils.lines("It was a Foo! " + Bar.class.getTypeName());

  private final TestParameters parameters;

  public CompatKeepInterfaceAsInstantiatedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testCastReference() throws Exception {
    testReference(TestCast.class);
  }

  @Test
  public void testCastPG() throws Exception {
    testCast(testForProguard()).assertSuccessWithOutput(expected);
  }

  @Test
  public void testCastR8() throws Exception {
    testCast(testForR8Compat(parameters.getBackend())).assertSuccessWithOutput(expected);
  }

  @Test
  public void testInstanceOfReference() throws Exception {
    testReference(TestInstanceOf.class);
  }

  @Test
  public void testInstanceOfPG() throws Exception {
    testInstanceOf(testForProguard()).assertSuccessWithOutput(expected);
  }

  @Test
  public void testInstanceOfR8() throws Exception {
    testInstanceOf(testForR8Compat(parameters.getBackend()))
        // TODO(b/140471200): The compatibility behavior should keep Foo.
        .assertFailureWithErrorThatMatches(containsString("NoClassDefFoundError"));
  }

  private TestRunResult<?> testCast(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    return testShrinker(builder, TestCast.class);
  }

  private TestRunResult<?> testInstanceOf(TestShrinkerBuilder<?, ?, ?, ?, ?> builder)
      throws Exception {
    return testShrinker(builder, TestInstanceOf.class);
  }

  private void testReference(Class<?> main) throws Exception {
    testForJvm(parameters)
        .addProgramClasses(main, Foo.class, Bar.class)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(expected);
  }

  private TestRunResult<?> testShrinker(TestShrinkerBuilder<?, ?, ?, ?, ?> builder, Class<?> main)
      throws Exception {
    return builder
        // Add -dontwarn to avoid PG failing since this test runner class is not present.
        .applyIf(
            builder.isProguardTestBuilder(),
            b -> b.addDontWarn(CompatKeepInterfaceAsInstantiatedTest.class))
        .addDontObfuscate()
        .addProgramClasses(main, Foo.class)
        .addKeepMainRule(main)
        .compile()
        .addRunClasspathClasses(Bar.class)
        .run(parameters.getRuntime(), main);
  }
}
