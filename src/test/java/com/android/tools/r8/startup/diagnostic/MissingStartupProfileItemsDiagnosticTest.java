// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.diagnostic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.D8TestCompileResult;
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
    // In D8 the startup profile is used to relayout an existing apk. Therefore, we first compile
    // the program to dex, and then relayout the dex using D8 with a startup profile.
    D8TestCompileResult compileResult =
        testForD8(Backend.DEX)
            .addProgramClasses(Main.class)
            .release()
            .setMinApi(AndroidApiLevel.LATEST)
            .compile();
    testForD8(Backend.DEX)
        .addProgramFiles(compileResult.writeToZip())
        .addStartupProfileProviders(getStartupProfileProviders())
        .release()
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics);
  }

  @Test
  public void testR8() throws Exception {
    // In R8 we expect a startup profile that matches the input app. Since profiles gathered from
    // running on ART will include synthetics, and these synthetics are not in the input app, we do
    // not raise warnings if some rules in the profile do not match anything.
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addStartupProfileProviders(getStartupProfileProviders())
        .setMinApi(AndroidApiLevel.LATEST)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
  }

  private static Collection<StartupProfileProvider> getStartupProfileProviders() {
    StartupProfileProvider startupProfileProvider =
        new StartupProfileProvider() {
          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            ClassReference fooClassReference = Reference.classFromTypeName("Foo");
            ClassReference barClassReference = Reference.classFromTypeName("Bar");
            ClassReference jDollarClassReference = Reference.classFromTypeName("j$.Foo");
            startupProfileBuilder
                .addStartupClass(
                    startupClassBuilder -> startupClassBuilder.setClassReference(fooClassReference))
                .addStartupMethod(
                    startupMethodBuilder ->
                        startupMethodBuilder.setMethodReference(
                            MethodReferenceUtils.mainMethod(barClassReference)))
                .addStartupClass(
                    startupClassBuilder ->
                        startupClassBuilder.setClassReference(jDollarClassReference));
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
                        "Startup class not found: Foo")))));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
