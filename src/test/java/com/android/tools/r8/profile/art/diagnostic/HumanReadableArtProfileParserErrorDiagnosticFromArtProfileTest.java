// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.diagnostic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.ByteArrayInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HumanReadableArtProfileParserErrorDiagnosticFromArtProfileTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClasses(Main.class)
        .addArtProfileForRewriting(createArtProfileProvider())
        .release()
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(createArtProfileProvider())
        .allowDiagnosticInfoMessages()
        .release()
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics);
  }

  private ArtProfileProvider createArtProfileProvider() {
    return new ArtProfileProvider() {

      @Override
      public void getArtProfile(ArtProfileBuilder artProfileBuilder) {
        artProfileBuilder.addHumanReadableArtProfile(
            new UTF8TextInputStream(new ByteArrayInputStream("INVALID1\nINVALID2".getBytes())),
            ConsumerUtils.emptyConsumer());
      }

      @Override
      public Origin getOrigin() {
        return Origin.unknown();
      }
    };
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics.assertInfosMatch(
        allOf(
            diagnosticType(HumanReadableArtProfileParserErrorDiagnostic.class),
            diagnosticMessage(
                equalTo("Unable to parse rule at line 1 from ART profile: INVALID1"))),
        allOf(
            diagnosticType(HumanReadableArtProfileParserErrorDiagnostic.class),
            diagnosticMessage(
                equalTo("Unable to parse rule at line 2 from ART profile: INVALID2"))));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
