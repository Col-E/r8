// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.ClinitDeadlockAfterMergingSingletonClassesInstantiatedByCompanionTest.Host.Companion.HostA;
import com.android.tools.r8.classmerging.horizontal.ClinitDeadlockAfterMergingSingletonClassesInstantiatedByCompanionTest.Host.Companion.HostB;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClinitDeadlockAfterMergingSingletonClassesInstantiatedByCompanionTest
    extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public int thread;

  @Parameters(name = "{0}, thread: {1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), ImmutableList.of(1, 2, 3));
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void thread0();",
            "  public static void thread" + thread + "();",
            "}")
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        thread == 1, i -> i.assertIsCompleteMergeGroup(HostA.class, HostB.class))
                    .assertNoOtherClassesMerged())
        .addOptionsModification(
            options ->
                options.horizontalClassMergerOptions().setEnableClassInitializerDeadlockDetection())
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile();
  }

  static class Main {

    // @Keep
    public static void thread0() {
      // Takes the lock for Host, then Companion, and then waits to take the lock for HostA.
      System.out.println(Host.companion.a);
      System.out.println(Host.companion.b);
    }

    // @Keep
    public static void thread1() {
      // Ditto. In this case there is no risk of a deadlock, since one of thread0 and thread1 will
      // take the lock for Host and the other thread will then wait on the lock for Host to be
      // released.
      System.out.println(Host.companion.a);
      System.out.println(Host.companion.b);
    }

    // @Keep
    public static void thread2() {
      // Takes the lock for Companion, then HostA, and then waits to take the lock for its
      // superclass Host.
      System.out.println(new Host.Companion());
    }

    // @Keep
    public static void thread3() {
      // Takes the lock for HostA, and then waits to take the lock for its superclass Host.
      HostA.init();
    }
  }

  static class Host {

    static Companion companion = new Companion();

    static class Companion {

      Host a = new HostA();

      Host b = new HostB();

      static class HostA extends Host {

        @NeverInline
        static void init() {
          System.out.println("HostA.init");
        }
      }

      static class HostB extends Host {}
    }
  }
}
