// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallSiteOptimizationPinnedMethodOverridePropagationTest extends TestBase {

  private static final String CLASS_PREFIX =
      "com.android.tools.r8.ir.optimize.callsites.CallSiteOptimizationPinnedMethodOverridePropagationTest$";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compiled =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                Arg.class, Arg1.class, Arg2.class, Call.class, CallImpl.class, Main2.class)
            .addKeepRules(
                ImmutableList.of(
                    "-keep interface " + CLASS_PREFIX + "Arg",
                    "-keep interface "
                        + CLASS_PREFIX
                        + "Call { \npublic void print("
                        + CLASS_PREFIX
                        + "Arg); \n}",
                    "-keep class "
                        + CLASS_PREFIX
                        + "Main2 { \npublic static void main(java.lang.String[]); \npublic static "
                        + CLASS_PREFIX
                        + "Arg getArg1(); \npublic static "
                        + CLASS_PREFIX
                        + "Arg getArg2(); \npublic static "
                        + CLASS_PREFIX
                        + "Call getCaller(); \n}"))
            .enableNoVerticalClassMergingAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .setMinApi(parameters)
            .compile();
    compiled.run(parameters.getRuntime(), Main2.class).assertSuccessWithOutputLines("Arg1");
    testForD8()
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(compiled.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Arg1", "Arg2");
  }

  // Kept
  @NoVerticalClassMerging
  interface Arg {

    @NeverInline
    @NeverPropagateValue
    String getString();
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  static class Arg1 implements Arg {

    @Override
    @NeverInline
    @NeverPropagateValue
    public String getString() {
      return "Arg1";
    }
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  static class Arg2 implements Arg {

    @Override
    @NeverInline
    @NeverPropagateValue
    public String getString() {
      return "Arg2";
    }
  }

  @NoVerticalClassMerging
  interface Call {

    // Kept.
    @NeverInline
    @NeverPropagateValue
    void print(Arg arg);
  }

  @NoVerticalClassMerging
  static class CallImpl implements Call {

    @Override
    @NeverInline
    @NeverPropagateValue
    public void print(Arg arg) {
      System.out.println(arg.getString());
    }
  }

  @NoVerticalClassMerging
  static class Main2 {

    // Kept.
    public static void main(String[] args) {
      // This would propagate Arg1 to print while it should not.
      getCaller().print(new Arg1());
    }

    // Kept.
    public static Arg getArg1() {
      return new Arg1();
    }

    // Kept.
    public static Arg getArg2() {
      return new Arg2();
    }

    // Kept.
    public static Call getCaller() {
      return new CallSiteOptimizationPinnedMethodOverridePropagationTest.CallImpl();
    }
  }

  static class Main {

    public static void main(String[] args) {
      Arg arg1 = Main2.getArg1();
      Arg arg2 = Main2.getArg2();
      Call caller = Main2.getCaller();
      caller.print(arg1);
      caller.print(arg2);
    }
  }
}
