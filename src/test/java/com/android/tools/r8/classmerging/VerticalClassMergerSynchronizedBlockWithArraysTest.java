// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerSynchronizedBlockWithArraysTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // The 4.0.4 runtime will flakily mark threads as blocking and report DEADLOCKED.
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public VerticalClassMergerSynchronizedBlockWithArraysTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testOnRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(VerticalClassMergerSynchronizedBlockWithArraysTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!");
  }

  @Test
  public void testNoMergingOfClassUsedInMonitor()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(VerticalClassMergerSynchronizedBlockWithArraysTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!")
        .inspect(inspector -> assertThat(inspector.clazz(LockOne.class), isPresent()));
  }

  abstract static class LockOne {}

  static class LockTwo extends LockOne {}

  static class LockThree {}

  public static class Main {

    private static volatile boolean inLockThreeCritical = false;
    private static volatile boolean inLockTwoCritical = false;
    private static volatile boolean arnoldWillNotBeBack = false;

    private static volatile Thread t1 = new Thread(Main::lockThreeThenOne);
    private static volatile Thread t2 = new Thread(Main::lockTwoThenThree);
    private static volatile Thread t3 = new Thread(Main::arnold);

    static void synchronizedAccessThroughLocks(String arg) {
      System.out.print(arg);
    }

    public static void main(String[] args) {
      t1.start();
      t2.start();
      // This thread is started to ensure termination in case we are rewriting incorrectly.
      t3.start();

      while (!arnoldWillNotBeBack) {}
    }

    static void lockThreeThenOne() {
      synchronized (LockThree[].class) {
        inLockThreeCritical = true;
        while (!inLockTwoCritical) {}
        synchronized (LockOne[].class) {
          synchronizedAccessThroughLocks("Hello ");
        }
      }
    }

    static void lockTwoThenThree() {
      synchronized (LockTwo[].class) {
        inLockTwoCritical = true;
        while (!inLockThreeCritical) {}
        synchronized (LockThree[].class) {
          synchronizedAccessThroughLocks("World!");
        }
      }
    }

    static void arnold() {
      while (t1.getState() != State.TERMINATED || t2.getState() != State.TERMINATED) {
        if (t1.getState() == State.BLOCKED && t2.getState() == State.BLOCKED) {
          System.err.println("DEADLOCKED!");
          System.exit(1);
          break;
        }
      }
      arnoldWillNotBeBack = true;
    }
  }
}
