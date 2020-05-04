// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.Collection;
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

  @Parameterized.Parameters(name = "target: {0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public KotlinClassInlinerTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  private static boolean isLambda(DexClass clazz) {
    return !clazz.type.getPackageDescriptor().startsWith("kotlin") &&
        (isKStyleLambdaOrGroup(clazz) || isJStyleLambdaOrGroup(clazz));
  }

  private static boolean isKStyleLambdaOrGroup(DexClass clazz) {
    return clazz.superType.descriptor.toString().equals("Lkotlin/jvm/internal/Lambda;");
  }

  private static boolean isJStyleLambdaOrGroup(DexClass clazz) {
    return clazz.superType.descriptor.toString().equals("Ljava/lang/Object;") &&
        clazz.interfaces.size() == 1;
  }

  private static Predicate<DexType> createLambdaCheck(CodeInspector inspector) {
    Set<DexType> lambdaClasses =
        inspector.allClasses().stream()
            .filter(clazz -> isLambda(clazz.getDexProgramClass()))
            .map(clazz -> clazz.getDexProgramClass().type)
            .collect(Collectors.toSet());
    return lambdaClasses::contains;
  }

  @Test
  public void testJStyleLambdas() throws Exception {
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_inliner_lambda_j_style.MainKt";
    runTest(
        "class_inliner_lambda_j_style",
        mainClassName,
        false,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1"), isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful2$1"), isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful3$1"), isPresent());
        });

    runTest(
        "class_inliner_lambda_j_style",
        mainClassName,
        true,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          Predicate<DexType> lambdaCheck = createLambdaCheck(inspector);
          ClassSubject clazz = inspector.clazz(mainClassName);

          assertEquals(
              Sets.newHashSet(), collectAccessedTypes(lambdaCheck, clazz, "testStateless"));

          assertEquals(Sets.newHashSet(), collectAccessedTypes(lambdaCheck, clazz, "testStateful"));

          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1"),
              not(isPresent()));

          assertEquals(
              Sets.newHashSet(), collectAccessedTypes(lambdaCheck, clazz, "testStateful2"));

          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful2$1"),
              not(isPresent()));

          assertEquals(
              Sets.newHashSet(), collectAccessedTypes(lambdaCheck, clazz, "testStateful3"));

          assertThat(
              inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful3$1"),
              not(isPresent()));
        });
  }

  @Test
  public void testKStyleLambdas() throws Exception {
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_inliner_lambda_k_style.MainKt";
    runTest(
        "class_inliner_lambda_k_style",
        mainClassName,
        false,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1"),
              isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
              isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
              isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1"),
              isPresent());
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1"),
              isPresent());
          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1"),
              isPresent());
          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1"),
              isPresent());
          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1"),
              isPresent());
        });

    runTest(
        "class_inliner_lambda_k_style",
        mainClassName,
        true,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          Predicate<DexType> lambdaCheck = createLambdaCheck(inspector);
          ClassSubject clazz = inspector.clazz(mainClassName);

          assertEquals(
              Sets.newHashSet(),
              collectAccessedTypes(
                  lambdaCheck, clazz, "testKotlinSequencesStateless", "kotlin.sequences.Sequence"));

          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1"),
              not(isPresent()));

          assertEquals(
              Sets.newHashSet(),
              collectAccessedTypes(
                  lambdaCheck,
                  clazz,
                  "testKotlinSequencesStateful",
                  "int",
                  "int",
                  "kotlin.sequences.Sequence"));

          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1"),
              not(isPresent()));

          assertEquals(
              Sets.newHashSet(), collectAccessedTypes(lambdaCheck, clazz, "testBigExtraMethod"));

          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1"),
              not(isPresent()));
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1"),
              not(isPresent()));
          assertThat(
              inspector.clazz("class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1"),
              not(isPresent()));

          assertEquals(
              Sets.newHashSet(),
              collectAccessedTypes(lambdaCheck, clazz, "testBigExtraMethodReturningLambda"));

          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1"),
              not(isPresent()));
          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1"),
              not(isPresent()));
          assertThat(
              inspector.clazz(
                  "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1"),
              not(isPresent()));
        });
  }

  @Test
  public void testDataClass() throws Exception {
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_inliner_data_class.MainKt";
    runTest(
        "class_inliner_data_class",
        mainClassName,
        true,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          ClassSubject clazz = inspector.clazz(mainClassName);
          assertTrue(
              collectAccessedTypes(
                      type -> !type.toSourceString().startsWith("java."),
                      clazz,
                      "main",
                      String[].class.getCanonicalName())
                  .isEmpty());
          assertEquals(
              Lists.newArrayList(
                  "void kotlin.jvm.internal.Intrinsics.throwParameterIsNullException(java.lang.String)"),
              collectStaticCalls(clazz, "main", String[].class.getCanonicalName()));
        });
  }

  private Set<String> collectAccessedTypes(Predicate<DexType> isTypeOfInterest,
      ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
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

  protected void runTest(String folder, String mainClass,
      boolean enabled, AndroidAppInspector inspector) throws Exception {
    runTest(
        folder,
        mainClass,
        // TODO(jsjeon): Introduce @NeverInline to kotlinR8TestResources
        StringUtils.lines(
            "-neverinline class * { void test*State*(...); }",
            "-neverinline class * { void testBigExtraMethod(...); }",
            "-neverinline class * { void testBigExtraMethodReturningLambda(...); }"),
        options -> {
          options.enableInlining = true;
          options.enableClassInlining = enabled;
          options.enableLambdaMerging = false;

          // TODO(b/141719453): These limits should be removed if a possible or the test refactored.
          // Tests check if specific lambdas are inlined or not, where some of target lambdas have
          // at least 4 instructions.
          options.inliningInstructionLimit = 4;
          options.classInliningInstructionLimit = 40;

          // Class inlining depends on the processing order. We therefore insert all call graph
          // edges and verify that we can class inline everything under this condition.
          options.testing.addCallEdgesForLibraryInvokes = true;
        },
        inspector);
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
