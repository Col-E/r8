// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticLambdaClassProfileRewritingTest extends TestBase {

  private enum ArtProfileInputOutput {
    MAIN_METHOD,
    IMPLEMENTATION_METHOD;

    public ExternalArtProfile getArtProfile() {
      switch (this) {
        case MAIN_METHOD:
          // Profile containing Main.main(). Should be rewritten to include the two lambda classes
          // and their constructors.
          return ExternalArtProfile.builder()
              .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
              .build();
        case IMPLEMENTATION_METHOD:
          // Profile containing the two lambda implementation methods, Main.lambda$main${0,1}.
          // Should be rewritten to include the two lambda accessibility bridge methods.
          return ExternalArtProfile.builder()
              .addMethodRule(
                  Reference.method(
                      Reference.classFromClass(Main.class),
                      "lambda$main$0",
                      Collections.emptyList(),
                      null))
              .addMethodRule(
                  Reference.method(
                      Reference.classFromClass(Main.class),
                      "lambda$main$1",
                      Collections.emptyList(),
                      null))
              .build();
        default:
          throw new RuntimeException();
      }
    }

    public void inspect(
        ArtProfileInspector profileInspector, CodeInspector inspector, TestParameters parameters) {
      ClassSubject mainClassSubject = inspector.clazz(Main.class);
      assertThat(mainClassSubject, isPresent());

      MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
      assertThat(mainMethodSubject, isPresent());

      // Check the presence of the first lambda implementation method and the synthesized accessor.
      MethodSubject lambdaImplementationMethod =
          mainClassSubject.uniqueMethodWithOriginalName("lambda$main$0");
      assertThat(lambdaImplementationMethod, isPresent());

      MethodSubject lambdaAccessibilityBridgeMethod =
          mainClassSubject.uniqueMethodThatMatches(
              invokesMethod(lambdaImplementationMethod)::matches);
      assertThat(lambdaAccessibilityBridgeMethod, onlyIf(parameters.isDexRuntime(), isPresent()));

      // Check the presence of the second lambda implementation method and the synthesized accessor.
      MethodSubject otherLambdaImplementationMethod =
          mainClassSubject.uniqueMethodWithOriginalName("lambda$main$1");
      assertThat(otherLambdaImplementationMethod, isPresent());

      MethodSubject otherLambdaAccessibilityBridgeMethod =
          mainClassSubject.uniqueMethodThatMatches(
              invokesMethod(otherLambdaImplementationMethod)::matches);
      assertThat(
          otherLambdaAccessibilityBridgeMethod, onlyIf(parameters.isDexRuntime(), isPresent()));

      // Check the presence of the first lambda class and its methods.
      ClassSubject lambdaClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 0));
      assertThat(lambdaClassSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      MethodSubject lambdaInitializerSubject = lambdaClassSubject.uniqueInstanceInitializer();
      assertThat(lambdaInitializerSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      MethodSubject lambdaMainMethodSubject =
          lambdaClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
      assertThat(lambdaMainMethodSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      // Check the presence of the second lambda class and its methods.
      ClassSubject otherLambdaClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 1));
      assertThat(otherLambdaClassSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      MethodSubject otherLambdaInitializerSubject =
          otherLambdaClassSubject.uniqueInstanceInitializer();
      assertThat(otherLambdaInitializerSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      MethodSubject otherLambdaMainMethodSubject =
          otherLambdaClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
      assertThat(otherLambdaMainMethodSubject, onlyIf(parameters.isDexRuntime(), isPresent()));

      if (parameters.isCfRuntime()) {
        switch (this) {
          case MAIN_METHOD:
            profileInspector
                .assertContainsMethodRule(mainMethodSubject)
                .assertContainsNoOtherRules();
            break;
          case IMPLEMENTATION_METHOD:
            profileInspector
                .assertContainsMethodRules(
                    lambdaImplementationMethod, otherLambdaImplementationMethod)
                .assertContainsNoOtherRules();
            break;
          default:
            throw new RuntimeException();
        }
      } else {
        assert parameters.isDexRuntime();
        switch (this) {
          case MAIN_METHOD:
            // Since Main.main() is in the art profile, so should the two synthetic lambdas be along
            // with their initializers. Since Main.lambda$main$*() is not in the art profile, the
            // interface method implementation does not need to be included in the profile.
            profileInspector
                .assertContainsClassRules(lambdaClassSubject, otherLambdaClassSubject)
                .assertContainsMethodRules(
                    mainMethodSubject, lambdaInitializerSubject, otherLambdaInitializerSubject)
                .assertContainsNoOtherRules();
            break;
          case IMPLEMENTATION_METHOD:
            // Since Main.lambda$main$*() is in the art profile, so should the two accessibility
            // bridges be along with the main virtual methods of the lambda classes.
            profileInspector
                .assertContainsMethodRules(
                    lambdaImplementationMethod,
                    lambdaAccessibilityBridgeMethod,
                    lambdaMainMethodSubject,
                    otherLambdaImplementationMethod,
                    otherLambdaAccessibilityBridgeMethod,
                    otherLambdaMainMethodSubject)
                .assertContainsNoOtherRules();
            break;
          default:
            throw new RuntimeException();
        }
      }
    }
  }

  @Parameter(0)
  public ArtProfileInputOutput artProfileInputOutput;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ArtProfileInputOutput.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-neverinline class " + Main.class.getTypeName() + " { void lambda$main$*(); }")
        .addArtProfileForRewriting(artProfileInputOutput.getArtProfile())
        .enableProguardTestOptions()
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(
            (profileInspector, inspector) ->
                artProfileInputOutput.inspect(profileInspector, inspector, parameters))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      Runnable lambda =
          System.currentTimeMillis() > 0
              ? () -> System.out.println("Hello, world!")
              : () -> {
                throw new RuntimeException();
              };
      lambda.run();
    }
  }
}
