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
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
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
    // None of the items in the profile exist in the app.
    profileInspector.assertEmpty();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
