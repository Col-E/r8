// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.maindexlist.MainDexCheckCastInstanceOfDependencyTest.Provider.getUnknownObject;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexCheckCastInstanceOfDependencyTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexCheckCastInstanceOfDependencyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDependencyTracing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addMainDexRules("-keep class " + Main.class.getTypeName() + " { void foo(); }")
        .setMinApi(parameters)
        .collectMainDexClasses()
        .compile()
        .inspectMainDexClasses(
            mainDexClasses -> {
              assertEquals(
                  ImmutableSet.of(
                      DependencyInstanceOf.class.getTypeName(),
                      DependencyCheckCast.class.getTypeName(),
                      DependencyConstClass.class.getTypeName(),
                      Provider.class.getTypeName(),
                      Main.class.getTypeName()),
                  mainDexClasses);
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Hello InstanceOf",
            "Hello CheckCast",
            "class com.android.tools.r8.maindexlist.MainDexCheckCastInstanceOfDependencyTest"
                + "$DependencyConstClass");
  }

  public static class DependencyInstanceOf {}

  public static class DependencyCheckCast {}

  public static class DependencyConstClass {}

  public static class Provider {

    public static Object getUnknownObject(String[] args) {
      return args.length > 0 ? new DependencyInstanceOf() : new DependencyCheckCast();
    }
  }

  public static class Main {

    public static void testInstanceOf(String[] args) {
      if (!(getUnknownObject(args) instanceof DependencyInstanceOf)) {
        System.out.println("Hello InstanceOf");
      }
    }

    public static void testCheckCast(String[] args) {
      DependencyCheckCast dependency = (DependencyCheckCast) getUnknownObject(args);
      System.out.println("Hello CheckCast");
    }

    public static void testConstClass() {
      System.out.println(DependencyConstClass.class);
    }

    public static void main(String[] args) {
      testInstanceOf(args);
      testCheckCast(args);
      testConstClass();
    }

    public static void foo() {
      System.out.println("Hello World");
    }
  }
}
