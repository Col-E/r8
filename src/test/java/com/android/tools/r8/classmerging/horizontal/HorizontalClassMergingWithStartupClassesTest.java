// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergingWithStartupClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean includeStartupClasses;

  @Parameters(name = "{0}, include startup classes: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private List<Class<?>> getStartupClasses() {
    return includeStartupClasses
        ? ImmutableList.of(StartupA.class, StartupB.class)
        : Collections.emptyList();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        getStartupClasses().isEmpty(),
                        i ->
                            i.assertIsCompleteMergeGroup(
                                StartupA.class,
                                StartupB.class,
                                OnClickHandlerA.class,
                                OnClickHandlerB.class),
                        i ->
                            i.assertIsCompleteMergeGroup(StartupA.class, StartupB.class)
                                .assertIsCompleteMergeGroup(
                                    OnClickHandlerA.class, OnClickHandlerB.class))
                    .assertNoOtherClassesMerged())
        .addStartupProfileProviders(
            new StartupProfileProvider() {

              @Override
              public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
                for (Class<?> startupClass : getStartupClasses()) {
                  ClassReference startupClassReference = Reference.classFromClass(startupClass);
                  startupProfileBuilder.addStartupClass(
                      startupClassBuilder ->
                          startupClassBuilder.setClassReference(startupClassReference));
                }
              }

              @Override
              public Origin getOrigin() {
                return Origin.unknown();
              }
            })
        .allowDiagnosticInfoMessages(
            includeStartupClasses
                && parameters.isDexRuntime()
                && parameters
                    .getApiLevel()
                    .isGreaterThanOrEqualTo(apiLevelWithNativeMultiDexSupport()))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(
            diagnostics -> {
              if (includeStartupClasses
                  && parameters.isDexRuntime()
                  && parameters
                      .getApiLevel()
                      .isGreaterThanOrEqualTo(apiLevelWithNativeMultiDexSupport())) {
                diagnostics.assertInfosMatch(
                    diagnosticType(StartupClassesNonStartupFractionDiagnostic.class));
              } else {
                diagnostics.assertNoMessages();
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("StartupA", "StartupB");
  }

  static class Main {

    public static void main(String[] args) {
      StartupA.foo();
      StartupB.bar();
    }

    // @Keep
    public void onClick() {
      OnClickHandlerA.baz();
      OnClickHandlerB.qux();
    }
  }

  static class StartupA {

    @NeverInline
    static void foo() {
      System.out.println("StartupA");
    }
  }

  static class StartupB {

    @NeverInline
    static void bar() {
      System.out.println("StartupB");
    }
  }

  static class OnClickHandlerA {

    @NeverInline
    static void baz() {
      System.out.println("IdleA");
    }
  }

  static class OnClickHandlerB {

    @NeverInline
    static void qux() {
      System.out.println("IdleB");
    }
  }
}
