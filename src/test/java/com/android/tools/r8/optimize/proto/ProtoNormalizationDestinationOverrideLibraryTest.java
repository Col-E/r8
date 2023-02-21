// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationDestinationOverrideLibraryTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String[] EXPECTED = new String[] {"LibraryMethod421337"};

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ProgramClass.class, X.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class, X.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .compile()
        .addBootClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class LibraryClass {

    public void foo(int i1, int i2, String bar) {
      System.out.println(bar + i1 + i2);
    }

    public static void callFoo(LibraryClass clazz) {
      clazz.foo(42, 1337, "LibraryMethod");
    }
  }

  public static class ProgramClass extends LibraryClass {
    @NeverInline
    @NoMethodStaticizing
    public void foo(String bar, int i1, int i2) {
      System.out.println(i1 + i2 + bar);
    }
  }

  // Needs to have a class name that is after lexicographically than ProgramClass.
  public static class X {
    @NeverInline
    public void foo(int i1, String bar, int i2) {
      System.out.println(i1 + bar + i2);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ProgramClass programClass = new ProgramClass();
      X otherProgramClass = new X();
      if (args.length == 1) {
        programClass.foo("Hello World", 1, 1);
        otherProgramClass.foo(1, "Hello World", 1);
      } else if (args.length > 1) {
        programClass.foo("Goodbye World", 2, 2);
        otherProgramClass.foo(2, "Goodbye World", 2);
      }
      LibraryClass.callFoo(programClass);
    }
  }
}
