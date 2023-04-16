// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtProfilePassthroughTest extends TestBase {

  enum ProviderStatus {
    PENDING,
    ACTIVE,
    DONE
  }

  private static final ClassReference mainClassReference = Reference.classFromClass(Main.class);
  private static final MethodReference mainMethodReference =
      MethodReferenceUtils.mainMethod(Main.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    ArtProfileProviderForTesting artProfileProvider = new ArtProfileProviderForTesting();
    ArtProfileConsumerForTesting residualArtProfileConsumer =
        new ArtProfileConsumerForTesting(artProfileProvider);
    testForD8(Backend.DEX)
        .addProgramClasses(Main.class)
        // Add a profile provider and consumer that verifies that the profile is being provided at
        // the same time it is being consumed.
        .addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer)
        .release()
        .setMinApi(AndroidApiLevel.LATEST)
        .compile();

    // Verify that the profile input was processed.
    assertTrue(residualArtProfileConsumer.finished);
    assertEquals(
        Lists.newArrayList(mainClassReference, mainMethodReference),
        residualArtProfileConsumer.references);
  }

  static class ArtProfileProviderForTesting implements ArtProfileProvider {

    ProviderStatus providerStatus = ProviderStatus.PENDING;

    @Override
    public void getArtProfile(ArtProfileBuilder profileBuilder) {
      providerStatus = ProviderStatus.ACTIVE;
      profileBuilder
          .addClassRule(classRuleBuilder -> classRuleBuilder.setClassReference(mainClassReference))
          .addMethodRule(
              methodRuleBuilder ->
                  methodRuleBuilder
                      .setMethodReference(mainMethodReference)
                      .setMethodRuleInfo(
                          methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsStartup(true)));
      providerStatus = ProviderStatus.DONE;
    }

    @Override
    public Origin getOrigin() {
      return Origin.unknown();
    }
  }

  static class ArtProfileConsumerForTesting implements ArtProfileConsumer {

    private final ArtProfileProviderForTesting artProfileProvider;

    List<Object> references = new ArrayList<>();
    boolean finished;

    ArtProfileConsumerForTesting(ArtProfileProviderForTesting artProfileProvider) {
      this.artProfileProvider = artProfileProvider;
    }

    @Override
    public ArtProfileRuleConsumer getRuleConsumer() {
      // The compiler should have fully requested the profile from the provider before getting the
      // consumer.
      assertEquals(ProviderStatus.DONE, artProfileProvider.providerStatus);
      return new ArtProfileRuleConsumer() {
        @Override
        public void acceptClassRule(
            ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
          references.add(classReference);
        }

        @Override
        public void acceptMethodRule(
            MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
          references.add(methodReference);
        }
      };
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished = true;
    }
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
