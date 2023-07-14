// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.sync;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.inliner.sync.InlineStaticSynchronizedMethodTest.TestClass.RunnableImpl;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineStaticSynchronizedMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineStaticSynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineStaticSynchronizedMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifySynchronizedMethodsAreInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "t1 START", "m1 ENTER", "t2 START", "m1 EXIT", "m2 ENTER", "m2 EXIT");
  }

  private void verifySynchronizedMethodsAreInlined(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(RunnableImpl.class);
    assertThat(classSubject, isPresent());
    // On M we are seeing issues when inlining code with monitors which will trip up some art
    // vms. See issue b/238399429 for details.
    if (parameters.isCfRuntime()
        || parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.M)) {
      int remaining = 0;
      remaining += classSubject.uniqueMethodWithOriginalName("m1").isPresent() ? 1 : 0;
      remaining += classSubject.uniqueMethodWithOriginalName("m2").isPresent() ? 1 : 0;
      assertEquals("Expected only one of m1 and m2 to be inlined", 1, remaining);
    } else {
      assertThat(classSubject.uniqueMethodWithOriginalName("m1"), not(isPresent()));
      assertThat(classSubject.uniqueMethodWithOriginalName("m2"), not(isPresent()));
    }
  }

  static class TestClass {

    private static List<String> logs = Collections.synchronizedList(new ArrayList<>());

    private static volatile Thread t1 = new Thread(new RunnableImpl(1));
    private static volatile Thread t2 = new Thread(new RunnableImpl(2));

    public static void main(String[] args) {
      System.out.println("t1 START");
      t1.start();
      waitUntil(() -> logs.contains("m1 ENTER"));
      System.out.println("t2 START");
      t2.start();
    }

    static void log(String message) {
      System.out.println(message);
      logs.add(message);
    }

    static void waitUntil(BooleanSupplier condition) {
      while (!condition.getAsBoolean()) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    static class RunnableImpl implements Runnable {

      int which;

      RunnableImpl(int which) {
        this.which = which;
      }

      @Override
      public void run() {
        if (which == 1) {
          m1();
        } else {
          m2();
        }
      }

      static synchronized void m1() {
        log("m1 ENTER");
        // Intentionally not using a lambda, since we do not allow inlining of methods with an
        // invoke-custom instruction. (This only makes a difference for the CF backend.)
        waitUntil(
            new BooleanSupplier() {

              @Override
              public boolean getAsBoolean() {
                return t2.getState() == State.BLOCKED;
              }
            });
        log("m1 EXIT");
      }

      static synchronized void m2() {
        log("m2 ENTER");
        log("m2 EXIT");
      }
    }
  }

  interface BooleanSupplier {

    boolean getAsBoolean();
  }
}
