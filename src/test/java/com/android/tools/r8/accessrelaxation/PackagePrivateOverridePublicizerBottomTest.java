// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateOverridePublicizerBottomTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"SubViewModel.clear()", "ViewModel.clear()"};
  private final String[] EXPECTED_ART_4 =
      new String[] {"SubViewModel.clear()", "SubViewModel.clear()"};
  private final String NEW_DESCRIPTOR = "Lfoo/bar/baz/SubViewModel;";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateOverridePublicizerBottomTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ViewModel.class)
        .addProgramClassFileData(
            getSubViewModelInAnotherPackage(), getRewrittenSubViewModelInMain())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ViewModel.class)
        .addProgramClassFileData(
            getSubViewModelInAnotherPackage(), getRewrittenSubViewModelInMain())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .allowAccessModification()
        .run(parameters.getRuntime(), Main.class)
        .apply(this::assertSuccessOutput)
        .inspect(
            inspector -> {
              ClassSubject subViewModelSubject =
                  inspector.clazz(DescriptorUtils.descriptorToJavaType(NEW_DESCRIPTOR));
              assertThat(subViewModelSubject, isPresent());
              MethodSubject clearSubject =
                  subViewModelSubject.uniqueMethodWithOriginalName("clear");
              assertThat(clearSubject, isPresent());
              assertTrue(clearSubject.isPublic());
            });
  }

  private byte[] getSubViewModelInAnotherPackage() throws Exception {
    return transformer(SubViewModel.class).setClassDescriptor(NEW_DESCRIPTOR).transform();
  }

  private byte[] getRewrittenSubViewModelInMain() throws Exception {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            DescriptorUtils.javaTypeToDescriptor(SubViewModel.class.getTypeName()), NEW_DESCRIPTOR)
        .transform();
  }

  private void assertSuccessOutput(TestRunResult<?> result) {
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      result.assertSuccessWithOutputLines(EXPECTED_ART_4);
    } else {
      result.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @SuppressWarnings("override") /* after changing the package the clear method is not overridden */
  @NoVerticalClassMerging
  public static class ViewModel {

    @NeverInline
    void clear() {
      System.out.println("ViewModel.clear()");
    }
  }

  @NeverClassInline
  @SuppressWarnings("override") /* after changing the package the clear method is not overridden */
  public static class /* foo.bar.baz. */ SubViewModel extends ViewModel {

    @NeverInline
    void clear() {
      System.out.println("SubViewModel.clear()");
    }

    public void callBridge() {
      clear();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      SubViewModel viewModel = new SubViewModel();
      viewModel.callBridge();
      ((ViewModel) viewModel).clear();
    }
  }
}
