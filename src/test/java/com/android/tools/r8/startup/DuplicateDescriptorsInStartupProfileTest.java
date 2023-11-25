// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateDescriptorsInStartupProfileTest extends TestBase {

  static final ClassReference MAIN_CLASS_REFERENCE = Reference.classFromClass(Main.class);
  static final MethodReference MAIN_METHOD_REFERENCE = MethodReferenceUtils.mainMethod(Main.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .allowDiagnosticMessages()
        .apply(
            testBuilder ->
                StartupTestingUtils.addStartupProfile(
                    testBuilder,
                    ImmutableList.of(
                        ExternalStartupClass.builder()
                            .setClassReference(MAIN_CLASS_REFERENCE)
                            .build(),
                        ExternalStartupMethod.builder()
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .build())))
        .apply(
            testBuilder ->
                StartupTestingUtils.addStartupProfile(
                    testBuilder,
                    ImmutableList.of(
                        ExternalStartupClass.builder()
                            .setClassReference(MAIN_CLASS_REFERENCE)
                            .build(),
                        ExternalStartupClass.builder()
                            .setClassReference(MAIN_CLASS_REFERENCE)
                            .build(),
                        ExternalStartupMethod.builder()
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .build(),
                        ExternalStartupMethod.builder()
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .build())))
        .setMinApi(AndroidApiLevel.L)
        .compile()
        .inspectDiagnosticMessages(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(StartupClassesNonStartupFractionDiagnostic.class)))
        .inspectMultiDex(
            primaryInspector -> assertThat(primaryInspector.clazz(Main.class), isPresent()),
            secondaryInspector ->
                assertThat(secondaryInspector.clazz(PostStartup.class), isPresent()));
  }

  static class Main {

    public static void main(String[] args) {}
  }

  static class PostStartup {}
}
