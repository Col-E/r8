// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.startup.utils.StartupTestingMatchers.isEqualToClassDataLayout;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.MixedSectionLayoutInspector;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.TypeReferenceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupSyntheticWithoutContextTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMinimalStartupDex;

  @Parameter(2)
  public boolean enableStartupCompletenessCheck;

  @Parameters(name = "{0}, minimal startup dex: {1}, completeness check: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // N so that java.util.function.Consumer is present.
        getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.N).build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    LinkedHashSet<ExternalStartupItem> startupList = new LinkedHashSet<>();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .apply(
            StartupTestingUtils.enableStartupInstrumentationForOriginalAppUsingLogcat(parameters))
        .release()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp))
        .run(parameters.getRuntime(), Main.class)
        .apply(StartupTestingUtils.removeStartupListFromStdout(startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput());
    assertEquals(getExpectedStartupList(), startupList);

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(A.class, C.class)
        .addOptionsModification(
            options -> {
              options
                  .getStartupOptions()
                  .setEnableMinimalStartupDex(enableMinimalStartupDex)
                  .setEnableStartupCompletenessCheckForTesting(enableStartupCompletenessCheck);
              options
                  .getTestingOptions()
                  .setMixedSectionLayoutStrategyInspector(getMixedSectionLayoutInspector());
            })
        .allowDiagnosticInfoMessages()
        .apply(testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, startupList))
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(StartupClassesNonStartupFractionDiagnostic.class)))
        .inspectMultiDex(this::inspectPrimaryDex, this::inspectSecondaryDex)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of("A", "B", "C");
  }

  private Set<ExternalStartupItem> getExpectedStartupList() throws NoSuchMethodException {
    return ImmutableSet.of(
        ExternalStartupClass.builder()
            .setClassReference(Reference.classFromClass(Main.class))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(MethodReferenceUtils.mainMethod(Main.class))
            .build(),
        ExternalStartupClass.builder().setClassReference(Reference.classFromClass(A.class)).build(),
        ExternalStartupMethod.builder()
            .setMethodReference(Reference.methodFromMethod(A.class.getDeclaredMethod("a")))
            .build(),
        ExternalStartupClass.builder().setClassReference(Reference.classFromClass(B.class)).build(),
        ExternalStartupMethod.builder()
            .setMethodReference(Reference.methodFromMethod(B.class.getDeclaredMethod("b")))
            .build(),
        ExternalStartupClass.builder()
            .setClassReference(getSyntheticLambdaClassReference(B.class))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(
                MethodReferenceUtils.instanceConstructor(getSyntheticLambdaClassReference(B.class)))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(
                Reference.method(
                    getSyntheticLambdaClassReference(B.class),
                    "run",
                    Collections.emptyList(),
                    TypeReferenceUtils.getVoidType()))
            .build(),
        ExternalStartupMethod.builder()
            .setMethodReference(Reference.methodFromMethod(B.class.getDeclaredMethod("lambda$b$0")))
            .build(),
        ExternalStartupClass.builder().setClassReference(Reference.classFromClass(C.class)).build(),
        ExternalStartupMethod.builder()
            .setMethodReference(Reference.methodFromMethod(C.class.getDeclaredMethod("c")))
            .build());
  }

  private List<ClassReference> getExpectedClassDataLayout(int virtualFile) {
    ImmutableList.Builder<ClassReference> builder = ImmutableList.builder();
    if (virtualFile == 0) {
      builder.add(
          Reference.classFromClass(Main.class),
          Reference.classFromClass(A.class),
          Reference.classFromClass(C.class),
          getSyntheticLambdaClassReference(B.class));
    }
    if (!enableMinimalStartupDex || virtualFile == 1) {
      builder.add(getSyntheticLambdaClassReference(Main.class));
    }
    return builder.build();
  }

  private MixedSectionLayoutInspector getMixedSectionLayoutInspector() {
    return new MixedSectionLayoutInspector() {
      @Override
      public void inspectClassDataLayout(int virtualFile, Collection<DexProgramClass> layout) {
        assertThat(layout, isEqualToClassDataLayout(getExpectedClassDataLayout(virtualFile)));
      }
    };
  }

  private void inspectPrimaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isAbsent());
    assertThat(inspector.clazz(C.class), isPresent());
    assertThat(inspector.clazz(getSyntheticLambdaClassReference(B.class)), isPresent());
    assertThat(
        inspector.clazz(getSyntheticLambdaClassReference(Main.class)),
        notIf(isPresent(), enableMinimalStartupDex));
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    if (enableMinimalStartupDex) {
      assertEquals(1, inspector.allClasses().size());
      assertThat(inspector.clazz(getSyntheticLambdaClassReference(Main.class)), isPresent());
    } else {
      assertTrue(inspector.allClasses().isEmpty());
    }
  }

  private static ClassReference getSyntheticLambdaClassReference(Class<?> synthesizingContext) {
    return SyntheticItemsTestUtils.syntheticLambdaClass(synthesizingContext, 0);
  }

  static class Main {

    public static void main(String[] args) {
      A.a();
      Runnable r = System.currentTimeMillis() > 0 ? B.b() : error();
      r.run();
      C.c();
    }

    static Runnable error() {
      return () -> {
        throw new RuntimeException();
      };
    }
  }

  static class A {

    static void a() {
      System.out.println("A");
    }
  }

  // Class B will be pruned as a result of inlining, yet the synthetic derived from B.b() remains in
  // the startup list.
  static class B {

    static Runnable b() {
      return () -> System.out.println("B");
    }
  }

  static class C {

    static void c() {
      System.out.println("C");
    }
  }
}
