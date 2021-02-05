// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexListMergeInRootTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MainDexListMergeInRootTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMainDexTracing() {
    assumeTrue(parameters.getDexRuntimeVersion().isDalvik());
    assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(OutsideMainDex.class, InsideA.class, InsideB.class, Main.class)
                .addKeepClassAndMembersRules(Main.class)
                .setMinApi(parameters.getApiLevel())
                .enableNeverClassInliningAnnotations()
                .enableNoHorizontalClassMergingAnnotations()
                .enableInliningAnnotations()
                .noMinification()
                .addMainDexRules(
                    "-keep class "
                        + Main.class.getTypeName()
                        + " { public static void main(***); }")
                .addOptionsModification(
                    options -> {
                      options.testing.checkForNotExpandingMainDexTracingResult = true;
                    })
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString(
                                  "Class com.android.tools.r8.maindexlist"
                                      + ".MainDexListMergeInRootTest$OutsideMainDex"
                                      + " was not a main dex root in the first round")));
                    }));
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  public static class OutsideMainDex {

    @NeverInline
    public void print(int i) {
      System.out.println("OutsideMainDex::print" + i);
    }
  }

  @NeverClassInline
  public static class InsideA {

    public void bar() {
      System.out.println("A::live");
    }

    /* Not a traced root */
    @NeverInline
    public void foo(int i) {
      new OutsideMainDex().print(i);
    }
  }

  @NeverClassInline
  public static class InsideB {

    @NeverInline
    public void foo(int i) {
      System.out.println("InsideB::live" + i);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new InsideB().foo(args.length);
      new InsideA().bar();
    }

    public void keptToKeepInsideANotLive() {
      new InsideA().foo(System.currentTimeMillis() > 0 ? 0 : 1);
    }
  }
}
