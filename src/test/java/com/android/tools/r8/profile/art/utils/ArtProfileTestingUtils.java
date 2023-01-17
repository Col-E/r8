// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileClassRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.ArtProfileRuleConsumer;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

public class ArtProfileTestingUtils {

  /**
   * Adds the given {@param artProfile} as an ART profile for rewriting. The residual ART profile
   * will be forwarded to the given test inspector, {@param residualArtProfileInspector}.
   */
  public static void addArtProfileForRewriting(
      ExternalArtProfile artProfile,
      Consumer<ExternalArtProfile> residualArtProfileInspector,
      R8TestBuilder<?> testBuilder) {
    // Provider for passing the original ART profile to the compilation.
    ArtProfileProvider artProfileProvider =
        new ArtProfileProvider() {

          @Override
          public void getArtProfile(ArtProfileBuilder profileBuilder) {
            artProfile.forEach(
                classRule ->
                    profileBuilder.addClassRule(
                        classRuleBuilder ->
                            classRuleBuilder.setClassReference(classRule.getClassReference())),
                methodRule ->
                    profileBuilder.addMethodRule(
                        methodRuleBuilder ->
                            methodRuleBuilder.setMethodReference(methodRule.getMethodReference())));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        };

    // Consumer for accepting the residual ART profile from the compilation.
    ArtProfileConsumer residualArtProfileConsumer =
        new ArtProfileConsumer() {

          final ExternalArtProfile.Builder residualArtProfileBuilder = ExternalArtProfile.builder();

          @Override
          public ArtProfileRuleConsumer getRuleConsumer() {
            return new ArtProfileRuleConsumer() {

              @Override
              public void acceptClassRule(
                  ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
                residualArtProfileBuilder.addRule(
                    ExternalArtProfileClassRule.builder()
                        .setClassReference(classReference)
                        .build());
              }

              @Override
              public void acceptMethodRule(
                  MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
                residualArtProfileBuilder.addRule(
                    ExternalArtProfileMethodRule.builder()
                        .setMethodReference(methodReference)
                        .setMethodRuleInfo(methodRuleInfo)
                        .build());
              }
            };
          }

          @Override
          public void finished(DiagnosticsHandler handler) {
            residualArtProfileInspector.accept(residualArtProfileBuilder.build());
          }
        };

    testBuilder.addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer);
  }
}
