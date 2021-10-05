// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinClassInlinerTest extends AbstractR8KotlinTestBase {

  private final String INTERNAL_SYNTHETIC_MAIN_PREFIX =
      "class_inliner_lambda_j_style.MainKt$$InternalSyntheticLambda";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinClassInlinerTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(parameters, kotlinParameters, true);
  }

  @Test
  public void testJStyleLambdas() throws Exception {
    // SAM interfaces lambdas are implemented by invoke dynamic in kotlin 1.5 unlike 1.4 where a
    // class is generated for each. In CF we leave invokeDynamic but for DEX we desugar the classes
    // and merge them.
    boolean hasKotlinCGeneratedLambdaClasses = kotlinParameters.isOlderThan(KOTLINC_1_5_0);
    String mainClassName = "class_inliner_lambda_j_style.MainKt";
    runTest(
            "class_inliner_lambda_j_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules("-neverinline class * { void test*State*(...); }")
                    .addNoHorizontalClassMergingRule(
                        "class_inliner_lambda_j_style.SamIface$Consumer")
                    .addHorizontallyMergedClassesInspector(
                        inspector -> {
                          if (!hasKotlinCGeneratedLambdaClasses && testParameters.isCfRuntime()) {
                            inspector.assertNoClassesMerged();
                          } else if (!hasKotlinCGeneratedLambdaClasses) {
                            Set<Set<DexType>> mergeGroups = inspector.getMergeGroups();
                            assertEquals(2, mergeGroups.size());
                            IntBox seenLambdas = new IntBox();
                            assertTrue(
                                mergeGroups.stream()
                                    .flatMap(Collection::stream)
                                    .allMatch(
                                        type -> {
                                          boolean isDesugaredLambda =
                                              type.toSourceString()
                                                  .startsWith(INTERNAL_SYNTHETIC_MAIN_PREFIX);
                                          if (isDesugaredLambda) {
                                            seenLambdas.increment();
                                          }
                                          return isDesugaredLambda;
                                        }));
                          } else {
                            inspector
                                .assertIsCompleteMergeGroup(
                                    "class_inliner_lambda_j_style.MainKt$testStateless$1",
                                    "class_inliner_lambda_j_style.MainKt$testStateless$2",
                                    "class_inliner_lambda_j_style.MainKt$testStateless$3")
                                .assertIsCompleteMergeGroup(
                                    "class_inliner_lambda_j_style.MainKt$testStateful$1",
                                    "class_inliner_lambda_j_style.MainKt$testStateful$2",
                                    "class_inliner_lambda_j_style.MainKt$testStateful$2$1",
                                    "class_inliner_lambda_j_style.MainKt$testStateful$3",
                                    "class_inliner_lambda_j_style.MainKt$testStateful2$1",
                                    "class_inliner_lambda_j_style.MainKt$testStateful3$1");
                          }
                        })
                    .noClassInlining())
        .inspect(
            inspector -> {
              if (testParameters.isCfRuntime() && !hasKotlinCGeneratedLambdaClasses) {
                assertEquals(5, inspector.allClasses().size());
              } else if (!hasKotlinCGeneratedLambdaClasses) {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda1"),
                    isPresent());
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda2"),
                    isPresent());
              } else {
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateless$1"),
                    isPresent());
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1"),
                    isPresent());
              }
            });

    runTest(
            "class_inliner_lambda_j_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules("-neverinline class * { void test*State*(...); }")
                    .addNoHorizontalClassMergingRule(
                        "class_inliner_lambda_j_style.SamIface$Consumer"))
        .inspect(
            inspector -> {
              if (testParameters.isCfRuntime() && !hasKotlinCGeneratedLambdaClasses) {
                assertEquals(5, inspector.allClasses().size());
                return;
              }
              // TODO(b/173337498): MainKt$testStateless$1 should always be class inlined.
              if (!hasKotlinCGeneratedLambdaClasses) {
                assertThat(
                    inspector.clazz(
                        "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda1"),
                    isPresent());
              } else {
                assertThat(
                    inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateless$1"),
                    notIf(isPresent(), testParameters.isDexRuntime()));
              }

              // TODO(b/173337498): MainKt$testStateful$1 should be class inlined.
              assertThat(
                  inspector.clazz(
                      !hasKotlinCGeneratedLambdaClasses
                          ? "class_inliner_lambda_j_style.MainKt$$ExternalSyntheticLambda2"
                          : "class_inliner_lambda_j_style.MainKt$testStateful$1"),
                  isPresent());
            });
  }

  @Test
  public void testKStyleLambdas() throws Exception {
    String mainClassName = "class_inliner_lambda_k_style.MainKt";
    runTest(
            "class_inliner_lambda_k_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules(
                        "-neverinline class * { void test*State*(...); }",
                        "-neverinline class * { void testBigExtraMethod(...); }",
                        "-neverinline class * { void testBigExtraMethodReturningLambda(...); }")
                    .addHorizontallyMergedClassesInspector(
                        inspector ->
                            inspector.assertIsCompleteMergeGroup(
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1",
                                "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1"))
                    .noClassInlining())
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(
                      "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1"),
                  isPresent());
              assertThat(
                  inspector.clazz(
                      "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
                  isPresent());
              assertThat(
                  inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
                  isPresent());
            });

    runTest(
            "class_inliner_lambda_k_style",
            mainClassName,
            testBuilder ->
                testBuilder
                    // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
                    .addKeepRules(
                    "-neverinline class * { void test*State*(...); }",
                    "-neverinline class * { void testBigExtraMethod(...); }",
                    "-neverinline class * { void testBigExtraMethodReturningLambda(...); }"))
        .inspect(
            inspector -> {
              // TODO(b/173337498): Should be absent, but horizontal class merging interferes with
              //  class inlining.
              assertThat(
                  inspector.clazz(
                      "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1"),
                  isPresent());

              // TODO(b/173337498): Should be absent, but horizontal class merging interferes with
              //  class inlining.
              assertThat(
                  inspector.clazz(
                      "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
                  isPresent());

              // TODO(b/173337498): Should be absent, but horizontal class merging interferes with
              //  class inlining.
              assertThat(
                  inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
                  isPresent());
            });
  }

  @Test
  public void testDataClass() throws Exception {
    // TODO(b/179866251): Update tests.
    assumeTrue(kotlinc.is(KOTLINC_1_3_72) && testParameters.isDexRuntime());
    String mainClassName = "class_inliner_data_class.MainKt";
    runTest("class_inliner_data_class", mainClassName)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(mainClassName);
              assertEquals(
                  Collections.emptySet(),
                  collectAccessedTypes(
                      type -> !type.toSourceString().startsWith("java."),
                      clazz,
                      "main",
                      String[].class.getCanonicalName()));
              assertEquals(
                  Lists.newArrayList(
                      "void kotlin.jvm.internal.Intrinsics.throwParameterIsNullException(java.lang.String)"),
                  collectStaticCalls(clazz, "main", String[].class.getCanonicalName()));
            });
  }

  private Set<String> collectAccessedTypes(
      Predicate<DexType> isTypeOfInterest,
      ClassSubject clazz,
      String methodName,
      String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    // TODO(b/179866251): Allow for CF code here.
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return Stream.concat(
        filterInstructionKind(code, NewInstance.class)
            .map(insn -> ((NewInstance) insn).getType()),
        filterInstructionKind(code, SgetObject.class)
            .map(insn -> insn.getField().holder)
    )
        .filter(isTypeOfInterest)
        .map(DexType::toSourceString)
        .collect(Collectors.toSet());
  }

  private List<String> collectStaticCalls(ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    MethodSubject method = clazz.method(signature);
    return Streams.stream(method.iterateInstructions(InstructionSubject::isInvokeStatic))
        .map(insn -> insn.getMethod().toSourceString())
        .sorted()
        .collect(Collectors.toList());
  }
}
