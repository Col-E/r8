// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BoxedPrimitiveFromGenericUnboxingTest extends TestBase {

  @Parameter(0)
  public boolean enableBridgeHoistingToSharedSyntheticSuperclass;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, opt: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    boolean optimize =
        enableBridgeHoistingToSharedSyntheticSuperclass
            && parameters.canHaveNonReboundConstructorInvoke();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.enableBridgeHoistingToSharedSyntheticSuperclass =
                    enableBridgeHoistingToSharedSyntheticSuperclass)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Function should be removed as a result of bridge hoisting + inlining when adding a
              // shared superclass to Increment and Decrement, and another shared superclass to
              // StdoutPrinter and StderrPrinter.
              ClassSubject functionClassSubject = inspector.clazz(Function.class);
              assertThat(functionClassSubject, isAbsentIf(optimize));

              // Check that the cast to java.lang.Integer in Increment.apply has been removed as a
              // result of devirtualization.
              ClassSubject incrementClassSubject = inspector.clazz(Increment.class);
              assertThat(incrementClassSubject, isPresent());

              MethodSubject incrementApplyMethodSubject =
                  incrementClassSubject.uniqueMethodWithOriginalName("apply");
              assertThat(incrementApplyMethodSubject, isPresent());
              assertEquals(
                  optimize,
                  incrementApplyMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isCheckCast(Integer.class.getTypeName())));

              // Check that the cast to java.lang.String in StdoutPrinter.apply has been removed as
              // result of devirtualization (in fact the `Void apply(String)` method has been
              // optimized to `void apply()` as a result of constant propagation).
              ClassSubject stdoutPrinterClassSubject = inspector.clazz(StdoutPrinter.class);
              assertThat(stdoutPrinterClassSubject, isPresent());

              MethodSubject stdoutPrinterApplyMethodSubject =
                  stdoutPrinterClassSubject.uniqueMethodWithOriginalName("apply");
              assertThat(stdoutPrinterApplyMethodSubject, isPresent());
              assertEquals(
                  optimize,
                  stdoutPrinterApplyMethodSubject.getProgramMethod().getReturnType().isVoidType());
              assertEquals(
                  optimize ? 0 : 1, stdoutPrinterApplyMethodSubject.getParameters().size());
              assertEquals(
                  optimize,
                  stdoutPrinterApplyMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isCheckCast(String.class.getTypeName())));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "42", "42");
  }

  static class Main {

    public static void main(String[] args) {
      Function<Integer, Integer> inc =
          System.currentTimeMillis() > 0 ? new Increment() : new Decrement();
      Function<Integer, Integer> dec =
          System.currentTimeMillis() > 0 ? new Decrement() : new Increment();
      Function<String, Void> printer =
          System.currentTimeMillis() > 0 ? new StdoutPrinter() : new StderrPrinter();
      System.out.println(inc.apply(41));
      System.out.println(dec.apply(43));
      printer.apply("42");
    }
  }

  interface Function<S, T> {

    T apply(S s);
  }

  @NoHorizontalClassMerging
  static class Increment implements Function<Integer, Integer> {

    @Override
    public Integer apply(Integer i) {
      return i + 1;
    }
  }

  @NoHorizontalClassMerging
  static class Decrement implements Function<Integer, Integer> {

    @Override
    public Integer apply(Integer i) {
      return i - 1;
    }
  }

  @NoHorizontalClassMerging
  static class StdoutPrinter implements Function<String, Void> {

    @Override
    public Void apply(String obj) {
      System.out.println(obj);
      return null;
    }
  }

  @NoHorizontalClassMerging
  static class StderrPrinter implements Function<String, Void> {

    @Override
    public Void apply(String obj) {
      System.err.println(obj);
      return null;
    }
  }
}
