// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonEmptyToEmptyProfileRewritingTest extends TestBase {

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
        .compile()
        .inspect(this::inspect)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(Main.class.getDeclaredMethod("dead")))
        .build();
  }

  private void inspect(CodeInspector inspector) {
    // Verify Main.dead() is removed.
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());
    assertThat(mainClassSubject.uniqueMethodWithOriginalName("dead"), isAbsent());
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    // After shaking of Main.dead() the ART profile should become empty.
    profileInspector.assertEmpty();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }

    static void dead() {}
  }
}
