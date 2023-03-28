// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodCollisionAfterSyntheticSharingTest extends TestBase {

  @Parameter(0)
  public boolean enableSyntheticSharing;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, sharing: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        enableSyntheticSharing,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addOptionsModification(
                    options -> options.testing.enableSyntheticSharing = enableSyntheticSharing)
                .enableInliningAnnotations()
                .noHorizontalClassMergingOfSynthetics()
                .setMinApi(parameters)
                .compile()
                .inspect(
                    inspector -> {
                      ClassSubject mainClassSubject = inspector.clazz(Main.class);
                      assertThat(mainClassSubject, isPresent());

                      // Check that m(Object) has been strengthened to m(X) where X is a class in
                      // the program.
                      MethodSubject mObjectMethodSubject =
                          mainClassSubject.uniqueMethodThatMatches(
                              method ->
                                  method
                                      .getOriginalSignature()
                                      .toString()
                                      .equals("void m(java.lang.Object)"));
                      assertThat(mObjectMethodSubject, isPresent());
                      assertThat(
                          inspector.clazz(mObjectMethodSubject.getParameter(0).getTypeName()),
                          isPresent());

                      // Check that m(Runnable) has been strengthened to m(X) where X is a class in
                      // the program.
                      MethodSubject mRunnableMethodSubject =
                          mainClassSubject.uniqueMethodThatMatches(
                              method ->
                                  method
                                      .getOriginalSignature()
                                      .toString()
                                      .equals("void m(java.lang.Runnable)"));
                      assertThat(mRunnableMethodSubject, isPresent());
                      assertThat(
                          inspector.clazz(mRunnableMethodSubject.getParameter(0).getTypeName()),
                          isPresent());
                    })
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("Hello, world!", "Hello, world!"));
  }

  static class Main {

    public static void main(String[] args) {
      Runnable runnable = Main::greet;
      Runnable otherRunnable = Main::greet;
      Object otherRunnableInDisguise = otherRunnable;
      m(runnable);
      m(otherRunnableInDisguise);
    }

    @NeverInline
    static void m(Object otherRunnableInDisguise) {
      Runnable otherRunnable = (Runnable) otherRunnableInDisguise;
      otherRunnable.run();
    }

    @NeverInline
    static void m(Runnable runnable) {
      runnable.run();
    }

    static void greet() {
      System.out.println("Hello, world!");
    }
  }
}
