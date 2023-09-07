// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.startup.utils.StartupTestingMatchers.isEqualToClassDataLayout;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.desugar.LambdaClass;
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
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupSyntheticPlacementTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMinimalStartupDex;

  @Parameter(2)
  public boolean enableStartupCompletenessCheck;

  @Parameter(3)
  public boolean useLambda;

  @Parameters(name = "{0}, minimal startup dex: {1}, completeness check: {2}, use lambda: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        // N so that java.util.function.Consumer is present.
        getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.N).build(),
        BooleanUtils.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void testLayoutUsingD8() throws Exception {
    // First build the app using R8.
    R8TestCompileResult r8CompileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addKeepClassAndMembersRules(A.class, B.class, C.class)
            .addDontOptimize()
            .setMinApi(parameters)
            .compile();

    // Verify that the build works.
    r8CompileResult
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .assertSuccessWithOutputLines(getExpectedOutput());

    Path optimizedApp = r8CompileResult.writeToZip();

    // Then instrument the app to generate a startup list for the minified app.
    Set<ExternalStartupItem> startupList = new LinkedHashSet<>();
    testForD8(parameters.getBackend())
        .addProgramFiles(optimizedApp)
        .apply(
            StartupTestingUtils.enableStartupInstrumentationForOptimizedAppUsingLogcat(parameters))
        .release()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp))
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .apply(StartupTestingUtils.removeStartupListFromStdout(startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput())
        .apply(
            runResult ->
                assertEquals(
                    getExpectedStartupList(r8CompileResult.inspector(), false), startupList));

    // Finally rebuild the minified app using D8 and the startup list.
    testForD8(parameters.getBackend())
        .addProgramFiles(optimizedApp)
        .apply(
            testBuilder ->
                configureStartupOptions(testBuilder, r8CompileResult.inspector(), startupList))
        .release()
        .setMinApi(parameters)
        .compile()
        .inspectMultiDex(
            r8CompileResult.writeProguardMap(), this::inspectPrimaryDex, this::inspectSecondaryDex)
        .apply(this::checkCompleteness)
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  @Test
  public void testLayoutUsingR8() throws Exception {
    // First generate a startup list for the original app.
    Set<ExternalStartupItem> startupList = new LinkedHashSet<>();
    D8TestCompileResult instrumentationCompileResult =
        testForD8(parameters.getBackend())
            .addInnerClasses(getClass())
            .apply(
                StartupTestingUtils.enableStartupInstrumentationForOriginalAppUsingLogcat(
                    parameters))
            .release()
            .setMinApi(parameters)
            .compile();

    instrumentationCompileResult
        .addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp))
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .apply(StartupTestingUtils.removeStartupListFromStdout(startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput())
        .apply(
            runResult ->
                assertEquals(getExpectedStartupList(runResult.inspector(), true), startupList));

    // Then build the app using the startup list that is based on original names.
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(A.class, B.class, C.class)
        .allowDiagnosticMessages()
        .apply(
            testBuilder ->
                configureStartupOptions(
                    testBuilder, instrumentationCompileResult.inspector(), startupList))
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(StartupClassesNonStartupFractionDiagnostic.class)))
        .inspectMultiDex(this::inspectPrimaryDex, this::inspectSecondaryDex)
        .apply(this::checkCompleteness)
        .run(parameters.getRuntime(), Main.class, Boolean.toString(useLambda))
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private void checkCompleteness(TestCompileResult<?, ?> compileResult) throws Exception {
    if (enableStartupCompletenessCheck && !useLambda) {
      compileResult
          .run(parameters.getRuntime(), Main.class, "true")
          .assertFailureWithErrorThatThrows(NullPointerException.class);
    }
  }

  private void configureStartupOptions(
      TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder,
      CodeInspector inspector,
      Collection<ExternalStartupItem> startupList) {
    testBuilder
        .addOptionsModification(
            options -> {
              options
                  .getStartupOptions()
                  .setEnableMinimalStartupDex(enableMinimalStartupDex)
                  .setEnableStartupCompletenessCheckForTesting(enableStartupCompletenessCheck);
              options
                  .getTestingOptions()
                  .setMixedSectionLayoutStrategyInspector(
                      getMixedSectionLayoutInspector(inspector, testBuilder.isD8TestBuilder()));
            })
        .apply(ignore -> StartupTestingUtils.addStartupProfile(testBuilder, startupList));
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of("A", "B", "C");
  }

  @SuppressWarnings("unchecked")
  private Set<ExternalStartupItem> getExpectedStartupList(
      CodeInspector inspector, boolean isStartupListForOriginalApp) throws NoSuchMethodException {
    ImmutableSet.Builder<ExternalStartupItem> builder = ImmutableSet.builder();
    builder.add(
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
            .setMethodReference(
                Reference.methodFromMethod(B.class.getDeclaredMethod("b", boolean.class)))
            .build());
    if (useLambda) {
      builder.add(
          ExternalStartupMethod.builder()
              .setMethodReference(
                  Reference.methodFromMethod(B.class.getDeclaredMethod("synthesize")))
              .build());
      if (isStartupListForOriginalApp) {
        ClassReference syntheticLambdaClassReference = getSyntheticLambdaClassReference();
        builder.add(
            ExternalStartupClass.builder().setClassReference(syntheticLambdaClassReference).build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    MethodReferenceUtils.instanceConstructor(syntheticLambdaClassReference))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    Reference.method(
                        syntheticLambdaClassReference,
                        "accept",
                        Collections.singletonList(Reference.classFromClass(Object.class)),
                        TypeReferenceUtils.getVoidType()))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    Reference.method(
                        Reference.classFromClass(B.class),
                        "lambda$synthesize$0",
                        Collections.singletonList(Reference.classFromClass(Object.class)),
                        TypeReferenceUtils.getVoidType()))
                .build());
      } else {
        ClassSubject bClassSubject = inspector.clazz(B.class);

        MethodSubject syntheticLambdaAccessorMethod =
            bClassSubject.uniqueMethodThatMatches(
                method ->
                    method
                        .getOriginalName()
                        .startsWith(LambdaClass.R8_LAMBDA_ACCESSOR_METHOD_PREFIX));
        assertThat(syntheticLambdaAccessorMethod, isPresent());

        ClassSubject externalSyntheticLambdaClassSubject =
            inspector.clazz(getSyntheticLambdaClassReference());
        assertThat(externalSyntheticLambdaClassSubject, isPresent());

        ClassReference externalSyntheticLambdaClassReference =
            externalSyntheticLambdaClassSubject.getFinalReference();

        builder.add(
            ExternalStartupClass.builder()
                .setClassReference(externalSyntheticLambdaClassReference)
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    MethodReferenceUtils.instanceConstructor(externalSyntheticLambdaClassReference))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    Reference.method(
                        externalSyntheticLambdaClassReference,
                        "accept",
                        ImmutableList.of(Reference.classFromClass(Object.class)),
                        null))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    Reference.method(
                        Reference.classFromClass(B.class),
                        syntheticLambdaAccessorMethod.getFinalName(),
                        ImmutableList.of(Reference.classFromClass(Object.class)),
                        null))
                .build());
      }
      builder.add(
          ExternalStartupMethod.builder()
              .setMethodReference(
                  Reference.methodFromMethod(
                      B.class.getDeclaredMethod("lambda$synthesize$0", Object.class)))
              .build());
    }
    builder.add(
        ExternalStartupClass.builder().setClassReference(Reference.classFromClass(C.class)).build(),
        ExternalStartupMethod.builder()
            .setMethodReference(Reference.methodFromMethod(C.class.getDeclaredMethod("c")))
            .build());
    return builder.build();
  }

  private List<ClassReference> getExpectedClassDataLayout(
      CodeInspector inspector, boolean isD8, int virtualFile) {
    ClassSubject syntheticLambdaClassSubject = inspector.clazz(getSyntheticLambdaClassReference());

    // The synthetic lambda should only be placed alongside its synthetic context (B) if it is used.
    // Otherwise, it should be last, or in the second dex file if compiling with minimal startup.
    ImmutableList.Builder<ClassReference> layoutBuilder = ImmutableList.builder();
    if (virtualFile == 0) {
      layoutBuilder.add(
          Reference.classFromClass(Main.class),
          Reference.classFromClass(A.class),
          Reference.classFromClass(B.class));
      if (isD8) {
        if (useLambda) {
          layoutBuilder.add(syntheticLambdaClassSubject.getFinalReference());
        }
        layoutBuilder.add(Reference.classFromClass(C.class));
      } else {
        layoutBuilder.add(Reference.classFromClass(C.class));
        if (useLambda) {
          layoutBuilder.add(syntheticLambdaClassSubject.getFinalReference());
        }
      }
    }
    if (!useLambda) {
      if (!enableMinimalStartupDex || virtualFile == 1) {
        layoutBuilder.add(syntheticLambdaClassSubject.getFinalReference());
      }
    }
    return layoutBuilder.build();
  }

  private MixedSectionLayoutInspector getMixedSectionLayoutInspector(
      CodeInspector inspector, boolean isD8) {
    return new MixedSectionLayoutInspector() {
      @Override
      public void inspectClassDataLayout(int virtualFile, Collection<DexProgramClass> layout) {
        assertThat(
            layout,
            isEqualToClassDataLayout(getExpectedClassDataLayout(inspector, isD8, virtualFile)));
      }
    };
  }

  private void inspectPrimaryDex(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isPresent());
    assertThat(
        inspector.clazz(getSyntheticLambdaClassReference()),
        notIf(isPresent(), enableMinimalStartupDex && !useLambda));
  }

  private void inspectSecondaryDex(CodeInspector inspector) {
    if (enableMinimalStartupDex && !useLambda) {
      assertEquals(1, inspector.allClasses().size());
      assertThat(inspector.clazz(getSyntheticLambdaClassReference()), isPresent());
    } else {
      assertTrue(inspector.allClasses().isEmpty());
    }
  }

  private static ClassReference getSyntheticLambdaClassReference() {
    return SyntheticItemsTestUtils.syntheticLambdaClass(B.class, 0);
  }

  static class Main {

    public static void main(String[] args) {
      boolean useLambda = args.length > 0 && args[0].equals("true");
      A.a();
      B.b(useLambda);
      C.c();
    }
  }

  static class A {

    static void a() {
      System.out.println("A");
    }
  }

  static class B {

    static void b(boolean useLambda) {
      String message = System.currentTimeMillis() > 0 ? "B" : null;
      if (useLambda) {
        synthesize();
      }
      System.out.println(message);
    }

    static void synthesize() {
      Consumer<Object> consumer = obj -> {};
      consumer.accept(consumer);
    }
  }

  static class C {

    static void c() {
      System.out.println("C");
    }
  }
}
