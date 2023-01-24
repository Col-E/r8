// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticLambdaClassProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addRule(
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
                .build())
        .build();
  }

  private void inspect(CodeInspector inspector) {
    // Verify that two lambdas were synthesized when compiling to dex.
    assertThat(
        inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 0)),
        onlyIf(parameters.isDexRuntime(), isPresent()));
    assertThat(
        inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 1)),
        onlyIf(parameters.isDexRuntime(), isPresent()));
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    if (parameters.isCfRuntime()) {
      profileInspector.assertEqualTo(getArtProfile());
    } else {
      assert parameters.isDexRuntime();
      // TODO(b/265729283): Since Main.main() is in the art profile, so should the two synthetic
      //  lambdas be.
      profileInspector.assertEqualTo(getArtProfile());
    }
  }

  static class Main {

    public static void main(String[] args) {
      Runnable lambda =
          System.currentTimeMillis() > 0
              ? () -> System.out.println("Hello, world!")
              : () -> {
                throw new RuntimeException();
              };
      lambda.run();
    }
  }
}
