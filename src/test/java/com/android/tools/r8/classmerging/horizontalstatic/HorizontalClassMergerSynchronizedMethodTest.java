// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontalstatic;

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
public class HorizontalClassMergerSynchronizedMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // The 4.0.4 runtime will flakily mark threads as blocking and report DEADLOCKED.
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public HorizontalClassMergerSynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testOnRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(HorizontalClassMergerSynchronizedMethodTest.class)
        .run(parameters.getRuntime(), HorizontalClassMergerSynchronizedMethodTest.Main.class)
        .assertSuccessWithOutput("Hello World!");
  }

  @Test
  public void testNoMergingOfClassUsedInMonitor()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(HorizontalClassMergerSynchronizedMethodTest.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertClassesNotMerged(LockOne.class, LockTwo.class, LockThree.class)
                    .assertMergedInto(AcquireThree.class, AcquireOne.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!")
        .inspect(inspector -> assertThat(inspector.clazz(LockOne.class), isPresent()));
  }

  private interface I {

    void action();
  }

  // Must not be merged with LockTwo or LockThree.
  static class LockOne {

    static synchronized void acquire(I c) {
      c.action();
    }
  }

  // Must not be merged with LockOne or LockThree.
  public static class LockTwo {

    static synchronized void acquire(I c) {
      Main.inTwoCritical = true;
      while (!Main.inThreeCritical) {}
      c.action();
    }
  }

  // Must not be merged with LockOne or LockTwo.
  public static class LockThree {

    static synchronized void acquire(I c) {
      Main.inThreeCritical = true;
      while (!Main.inTwoCritical) {}
      c.action();
    }
  }

  public static class AcquireOne implements I {

    @Override
    public void action() {
      LockOne.acquire(() -> System.out.print("Hello "));
    }
  }

  public static class AcquireThree implements I {

    @Override
    public void action() {
      LockThree.acquire(() -> System.out.print("World!"));
    }
  }

  public static class Main {

    static volatile boolean inTwoCritical = false;
    static volatile boolean inThreeCritical = false;
    static volatile boolean arnoldWillNotBeBack = false;

    private static volatile Thread t1 = new Thread(Main::lockThreeThenOne);
    private static volatile Thread t2 = new Thread(Main::lockTwoThenThree);
    private static volatile Thread terminator = new Thread(Main::arnold);

    public static void main(String[] args) {
      t1.start();
      t2.start();
      // This thread is started to ensure termination in case we are rewriting incorrectly.
      terminator.start();

      while (!arnoldWillNotBeBack) {}
    }

    static void lockThreeThenOne() {
      LockThree.acquire(new AcquireOne());
    }

    static void lockTwoThenThree() {
      LockTwo.acquire(new AcquireThree());
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
