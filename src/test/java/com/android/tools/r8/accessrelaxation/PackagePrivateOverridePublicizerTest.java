// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateOverridePublicizerTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"SubViewModel.clear()", "ViewModel.clear()"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateOverridePublicizerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .allowAccessModification()
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  private void assertSuccessOutput(TestRunResult<?> result) {
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      result.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      result.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @NeverClassInline
  public static class SubViewModel extends ViewModel {

    @NeverInline
    public void clear() {
      System.out.println("SubViewModel.clear()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      SubViewModel subViewModel = new SubViewModel();
      subViewModel.clear();
      subViewModel.clearBridge();
    }
  }
}
