// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// If an exception class A is merged into another exception class B, then all exception tables
// should be updated, and class A should be removed entirely.
@RunWith(Parameterized.class)
public class ExceptionTablesTest extends VerticalClassMergerTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ExceptionTablesTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testClassesHaveBeenMerged() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ExceptionTablesTest.class)
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(this::inspectVerticallyMergedClasses)
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.enableLir())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspectVerticallyMergedClasses(VerticallyMergedClassesInspector inspector) {
    inspector.assertMergedIntoSubtype(ExceptionA.class, Exception1.class);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), isPresent());
    assertThat(inspector.clazz(ExceptionB.class), isPresent());
    assertThat(inspector.clazz(Exception2.class), isPresent());
    assertThat(inspector.clazz(ExceptionA.class), not(isPresent()));
    assertThat(inspector.clazz(Exception1.class), not(isPresent()));

    // Check that only two exception guard types remain.
    MethodSubject mainMethodSubject = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    Set<TypeSubject> guards =
        mainMethodSubject
            .streamTryCatches()
            .flatMap(tryCatch -> tryCatch.guards().stream())
            .collect(Collectors.toSet());
    assertEquals(2, guards.size());
  }

  public static class TestClass {

    public static void main(String[] args) {
      // The following will lead to a catch handler for ExceptionA, which is merged into ExceptionB.
      try {
        doSomethingThatMightThrowExceptionB();
        doSomethingThatMightThrowException2();
      } catch (ExceptionB exception) {
        System.out.println("Caught exception: " + exception.getMessage());
      } catch (ExceptionA exception) {
        System.out.println("Caught exception: " + exception.getMessage());
      } catch (Exception2 exception) {
        System.out.println("Caught exception: " + exception.getMessage());
      } catch (Exception1 exception) {
        System.out.println("Caught exception: " + exception.getMessage());
      }
    }

    private static void doSomethingThatMightThrowExceptionB() throws ExceptionB {
      if (System.nanoTime() > 0) {
        throw new ExceptionB("Ouch!");
      }
    }

    private static void doSomethingThatMightThrowException2() throws Exception2 {
      if (System.nanoTime() > 0) {
        throw new Exception2("Ouch!");
      }
    }
  }

  // Will be merged into ExceptionB when class merging is enabled.
  public static class ExceptionA extends Exception {
    public ExceptionA(String message) {
      super(message);
    }
  }

  public static class ExceptionB extends ExceptionA {
    public ExceptionB(String message) {
      super(message);
    }
  }

  public static class Exception1 extends Exception {
    public Exception1(String message) {
      super(message);
    }
  }

  public static class Exception2 extends Exception1 {
    public Exception2(String message) {
      super(message);
    }
  }
}
