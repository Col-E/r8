// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtProfileRewritingTest extends TestBase {

  private static final ClassReference mainClassReference = Reference.classFromClass(Main.class);
  private static final MethodReference mainMethodReference =
      MethodReferenceUtils.mainMethod(Main.class);

  private static final ClassReference greeterClassReference =
      Reference.classFromClass(Greeter.class);
  private static final MethodReference greetMethodReference =
      MethodReferenceUtils.methodFromMethod(Greeter.class, "greet");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    MyArtProfileInput artProfileInput = new MyArtProfileInput();
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options
                    .getArtProfileOptions()
                    .setArtProfileInputs(Collections.singleton(artProfileInput)))
        .enableInliningAnnotations()
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(inspector -> inspect(inspector, artProfileInput));
  }

  private void inspect(CodeInspector inspector, MyArtProfileInput artProfileInput) {
    ClassSubject greeterClassSubject = inspector.clazz(Greeter.class);
    assertThat(greeterClassSubject, isPresentAndRenamed());

    MethodSubject greetMethodSubject = greeterClassSubject.uniqueMethodWithName("greet");
    assertThat(greetMethodSubject, isPresentAndRenamed());

    assertTrue(artProfileInput.finished);
    assertEquals(
        Lists.newArrayList(
            mainClassReference,
            mainMethodReference,
            greeterClassSubject.getFinalReference(),
            greetMethodSubject.getFinalReference()),
        artProfileInput.references);
    assertEquals(
        Lists.newArrayList(
            ArtProfileClassRuleInfoImpl.empty(),
            ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build(),
            ArtProfileClassRuleInfoImpl.empty(),
            ArtProfileMethodRuleInfoImpl.builder().setIsHot().setIsPostStartup().build()),
        artProfileInput.infos);
  }

  static class MyArtProfileInput implements ArtProfileInput {

    boolean finished;
    List<Object> references = new ArrayList<>();
    List<Object> infos = new ArrayList<>();

    @Override
    public ResidualArtProfileConsumer getArtProfileConsumer() {
      return new ResidualArtProfileConsumer() {

        @Override
        public ResidualArtProfileRuleConsumer getRuleConsumer() {
          return new ResidualArtProfileRuleConsumer() {

            @Override
            public void acceptClassRule(
                ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
              references.add(classReference);
              infos.add(classRuleInfo);
            }

            @Override
            public void acceptMethodRule(
                MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
              references.add(methodReference);
              infos.add(methodRuleInfo);
            }
          };
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
          finished = true;
        }
      };
    }

    @Override
    public void getArtProfile(ArtProfileBuilder profileBuilder) {
      profileBuilder
          .addClassRule(classRuleBuilder -> classRuleBuilder.setClassReference(mainClassReference))
          .addMethodRule(
              methodRuleBuilder ->
                  methodRuleBuilder
                      .setMethodReference(mainMethodReference)
                      .setMethodRuleInfo(
                          methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsStartup(true)))
          .addClassRule(
              classRuleBuilder -> classRuleBuilder.setClassReference(greeterClassReference))
          .addMethodRule(
              methodRuleBuilder ->
                  methodRuleBuilder
                      .setMethodReference(greetMethodReference)
                      .setMethodRuleInfo(
                          methodRuleInfoBuilder ->
                              methodRuleInfoBuilder.setIsHot(true).setIsPostStartup(true)));
    }
  }

  static class Main {

    public static void main(String[] args) {
      Greeter.greet();
    }
  }

  static class Greeter {

    @NeverInline
    static void greet() {
      System.out.println("Hello, world!");
    }
  }
}
