// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.api;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyArtProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .addArtProfileForRewriting(getArtProfileProvider())
        .release()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfileProvider())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile);
  }

  private ArtProfileProvider getArtProfileProvider() {
    return new ArtProfileProvider() {

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        // Intentionally empty.
      }

      @Override
      public Origin getOrigin() {
        throw new Unreachable();
      }
    };
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    profileInspector.assertEmpty();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
