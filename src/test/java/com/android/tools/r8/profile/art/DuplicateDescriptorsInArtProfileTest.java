// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateDescriptorsInArtProfileTest extends TestBase {

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
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(
            new ArtProfileProvider() {
              @Override
              public void getArtProfile(ArtProfileBuilder profileBuilder) {
                profileBuilder.addClassRule(
                    classRuleBuilder -> classRuleBuilder.setClassReference(MAIN_CLASS_REFERENCE));
                profileBuilder.addClassRule(
                    classRuleBuilder -> classRuleBuilder.setClassReference(MAIN_CLASS_REFERENCE));
                profileBuilder.addMethodRule(
                    methodRuleBuilder ->
                        methodRuleBuilder
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .setMethodRuleInfo(
                                methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsHot(true)));
                profileBuilder.addMethodRule(
                    methodRuleBuilder ->
                        methodRuleBuilder
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .setMethodRuleInfo(
                                methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsStartup(true)));
                profileBuilder.addMethodRule(
                    methodRuleBuilder ->
                        methodRuleBuilder
                            .setMethodReference(MAIN_METHOD_REFERENCE)
                            .setMethodRuleInfo(
                                methodRuleInfoBuilder ->
                                    methodRuleInfoBuilder.setIsPostStartup(true)));
              }

              @Override
              public Origin getOrigin() {
                return Origin.unknown();
              }
            })
        .compile()
        .inspectResidualArtProfile(
            profileInspector ->
                profileInspector
                    .assertContainsClassRule(MAIN_CLASS_REFERENCE)
                    .inspectMethodRule(
                        MAIN_METHOD_REFERENCE,
                        methodInspector ->
                            methodInspector.assertIsHot().assertIsStartup().assertIsPostStartup())
                    .assertContainsNoOtherRules());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
