// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/171369796.
@RunWith(Parameterized.class)
public class PackagePrivateFinalOverrideInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateFinalOverrideInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ViewModel.class, I.class, Zoolander.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ViewModel.class, I.class, Zoolander.class, Main.class)
        .addKeepClassAndMembersRules(ViewModel.class)
        .addKeepClassAndMembersRules(I.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertResult);
  }

  public void assertResult(TestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      runResult.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      runResult.assertSuccessWithOutputLines("Zoolander::clear()");
    }
  }

  public interface I {
    void clear();
  }

  @NeverClassInline
  public static class Zoolander extends ViewModel implements I {

    @Override
    @NeverInline
    public final void clear() {
      System.out.println("Zoolander::clear()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      runClear(args.length == 0 ? new Zoolander() : null);
    }

    public static void runClear(I i) {
      i.clear();
    }
  }
}
