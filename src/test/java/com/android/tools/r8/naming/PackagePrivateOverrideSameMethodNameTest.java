// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateOverrideSameMethodNameTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"SubViewModel.clear()", "ViewModel.clear()"};
  private final boolean minification;

  @Parameters(name = "{0}, minification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public PackagePrivateOverrideSameMethodNameTest(TestParameters parameters, boolean minification) {
    this.parameters = parameters;
    this.minification = minification;
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(minification);
    testForRuntime(parameters)
        .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .minification(minification)
            .run(parameters.getRuntime(), Main.class)
            .apply(this::assertSuccessOutput);
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      runResult.inspectFailure(this::inspect);
    } else {
      runResult.inspect(this::inspect);
    }
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject viewModel = inspector.clazz(ViewModel.class);
    assertThat(viewModel, isPresentAndRenamed(minification));
    ClassSubject subViewModel = inspector.clazz(SubViewModel.class);
    assertThat(subViewModel, isPresentAndRenamed(minification));
    MethodSubject viewModelClear = viewModel.uniqueMethodWithName("clear");
    assertThat(viewModelClear, isPresentAndRenamed(minification));
    MethodSubject subViewModelClear = subViewModel.uniqueMethodWithName("clear");
    assertThat(subViewModelClear, isPresentAndRenamed(minification));
    assertEquals(viewModelClear.getFinalName(), subViewModelClear.getFinalName());
    if (!minification) {
      assertEquals("clear", viewModelClear.getFinalName());
    }
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
