// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoSuchClassAndMethodProfileRewritingTest extends TestBase {

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
        .setMinApi(parameters)
        // TODO(b/266178791): Emit a warning for each discarded item.
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile() {
    ClassReference missingClassReference = Reference.classFromDescriptor("Lfoo/Missing;");
    return ExternalArtProfile.builder()
        .addClassRule(missingClassReference)
        .addMethodRule(Reference.methodFromDescriptor(missingClassReference, "m", "()V"))
        .build();
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    // None of the items in the profile exist in the input.
    // TODO(b/266178791): Discard items from profile that is not in the input app.
    profileInspector.assertNotEmpty();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
