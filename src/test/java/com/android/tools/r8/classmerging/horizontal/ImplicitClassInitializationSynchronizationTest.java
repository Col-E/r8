// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.lang.Thread.State;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImplicitClassInitializationSynchronizationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesNotMerged(B.class, C.class))
        .addOptionsModification(
            options ->
                options.horizontalClassMergerOptions().setEnableClassInitializerDeadlockDetection())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private static List<String> getExpectedOutput() {
    return ImmutableList.of(
        "Main: fork",
        "Main: wait",
        "Worker: notify",
        "Worker: wait",
        "Main: notified",
        "Main: lock C",
        "Worker: notified",
        "Worker: lock B",
        "B",
        "Worker: unlock B",
        "C",
        "Main: unlock C");
  }

  static class Main {

    static Object lock = new Object();
    static Thread mainThread = Thread.currentThread();

    public static void main(String[] args) throws Exception {
      System.out.println("Main: fork");
      Thread workerThread = new Thread(A::new);
      workerThread.start();

      // Wait for the worker thread to take the lock for A.
      System.out.println("Main: wait");
      synchronized (lock) {
        lock.wait();
      }

      // Wait for the worker thread to be waiting on the main thread.
      while (workerThread.getState() != State.WAITING) {
        Thread.sleep(100);
      }

      System.out.println("Main: notified");

      // In one second, let the worker thread continue.
      doAfter(
          1000,
          () -> {
            synchronized (lock) {
              lock.notify();
            }
          });

      // In five seconds, report a dead lock.
      doAfter(5000, () -> System.exit(1));

      System.out.println("Main: lock C");
      System.out.println(new C());
      System.out.println("Main: unlock C");

      // No deadlock, success.
      System.exit(0);
    }

    private static void doAfter(int ms, Runnable runnable) {
      new Thread(
              () -> {
                try {
                  Thread.sleep(ms);
                  runnable.run();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              })
          .start();
    }
  }

  static class A {

    static {
      try {
        // Wait for the main thread to be waiting on the worker thread.
        while (Main.mainThread.getState() != State.WAITING) {
          Thread.sleep(100);
        }

        System.out.println("Worker: notify");
        synchronized (Main.lock) {
          Main.lock.notify();
        }

        // Wait for the main thread to take the lock for B.
        System.out.println("Worker: wait");
        synchronized (Main.lock) {
          Main.lock.wait();
        }
        System.out.println("Worker: notified");

        // Try to take the lock for B.
        System.out.println("Worker: lock B");
        System.out.println(new B());
        System.out.println("Worker: unlock B");
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class C extends A {

    @Override
    public String toString() {
      return "C";
    }
  }
}
