// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateNonOverridePublicizerTest extends TestBase {

  private final TestParameters parameters;
  private final boolean allowAccessModification;
  private final String[] EXPECTED =
      new String[] {"SubViewModel.clearNotOverriding()", "ViewModel.clear()"};

  @Parameters(name = "{0}, allowAccessModification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public PackagePrivateNonOverridePublicizerTest(
      TestParameters parameters, boolean allowAccessModification) {
    this.parameters = parameters;
    this.allowAccessModification = allowAccessModification;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ViewModel.class, SubViewModel.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .applyIf(allowAccessModification, TestShrinkerBuilder::allowAccessModification)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              // ViewModel.clear() is package private. When we publicize the method, we can inline
              // the clearBridge() into Main and thereby remove the ViewModel class entirely.
              ClassSubject clazz = inspector.clazz(ViewModel.class);
              assertThat(
                  clazz,
                  notIf(
                      isPresent(),
                      allowAccessModification
                          || parameters.isAccessModificationEnabledByDefault()));
            });
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class SubViewModel extends ViewModel {

    @NeverInline
    public void clearNotOverriding() {
      System.out.println("SubViewModel.clearNotOverriding()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      SubViewModel subViewModel = new SubViewModel();
      subViewModel.clearNotOverriding();
      subViewModel.clearBridge();
    }
  }
}
