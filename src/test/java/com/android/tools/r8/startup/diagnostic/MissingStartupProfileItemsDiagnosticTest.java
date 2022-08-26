// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.diagnostic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingStartupProfileItemsDiagnosticTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addStartupProfileProviders(getStartupProfileProviders())
        .release()
        .setIntermediate(true)
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addStartupProfileProviders(getStartupProfileProviders())
        .allowDiagnosticWarningMessages()
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics);
  }

  private static Collection<StartupProfileProvider> getStartupProfileProviders() {
    StartupProfileProvider startupProfileProvider =
        new StartupProfileProvider() {
          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            ClassReference fooClassReference = Reference.classFromTypeName("Foo");
            ClassReference barClassReference = Reference.classFromTypeName("Bar");
            ClassReference bazClassReference = Reference.classFromTypeName("Baz");
            startupProfileBuilder
                .addStartupClass(
                    startupClassBuilder -> startupClassBuilder.setClassReference(fooClassReference))
                .addStartupMethod(
                    startupMethodBuilder ->
                        startupMethodBuilder.setMethodReference(
                            MethodReferenceUtils.mainMethod(barClassReference)))
                .addSyntheticStartupMethod(
                    syntheticStartupMethodBuilder ->
                        syntheticStartupMethodBuilder.setSyntheticContextReference(
                            bazClassReference));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        };
    return Collections.singleton(startupProfileProvider);
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics.assertWarningsMatch(
        allOf(
            diagnosticType(MissingStartupProfileItemsDiagnostic.class),
            diagnosticMessage(
                equalTo(
                    StringUtils.joinLines(
                        "Startup method not found: void Bar.main(java.lang.String[])",
                        "Startup class not found: Baz",
                        "Startup class not found: Foo")))));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
