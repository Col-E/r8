// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class KotlinClassInlinerTest extends AbstractR8KotlinTestBase {
  @Parameters(name = "allowAccessModification: {0} target: {1}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> builder = new ImmutableList.Builder<>();
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      builder.add(new Object[]{Boolean.TRUE, targetVersion});
    }
    return builder.build();
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

  private static Predicate<DexType> createLambdaCheck(DexInspector inspector) {
    Set<DexType> lambdaClasses = inspector.allClasses().stream()
        .filter(clazz -> isLambda(clazz.getDexClass()))
        .map(clazz -> clazz.getDexClass().type)
        .collect(Collectors.toSet());
    return lambdaClasses::contains;
  }

  @Test
  public void testJStyleLambdas() throws Exception {
    final String mainClassName = "class_inliner_lambda_j_style.MainKt";
    runTest("class_inliner_lambda_j_style", mainClassName, false, (app) -> {
      DexInspector inspector = new DexInspector(app);
      assertTrue(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1").isPresent());
      assertTrue(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful2$1").isPresent());
      assertTrue(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful3$1").isPresent());
    });

    runTest("class_inliner_lambda_j_style", mainClassName, true, (app) -> {
      DexInspector inspector = new DexInspector(app);
      Predicate<DexType> lambdaCheck = createLambdaCheck(inspector);
      ClassSubject clazz = inspector.clazz(mainClassName);

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testStateless"));

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testStateful"));

      assertFalse(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1").isPresent());

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testStateful2"));

      assertFalse(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful2$1").isPresent());

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testStateful3"));

      assertFalse(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful3$1").isPresent());
    });
  }

  @Test
  public void testKStyleLambdas() throws Exception {
    final String mainClassName = "class_inliner_lambda_k_style.MainKt";
    runTest("class_inliner_lambda_k_style", mainClassName, false, (app) -> {
      DexInspector inspector = new DexInspector(app);
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1").isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1").isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1").isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1").isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1").isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1")
          .isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1")
          .isPresent());
      assertTrue(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1")
          .isPresent());
    });

    runTest("class_inliner_lambda_k_style", mainClassName, true, (app) -> {
      DexInspector inspector = new DexInspector(app);
      Predicate<DexType> lambdaCheck = createLambdaCheck(inspector);
      ClassSubject clazz = inspector.clazz(mainClassName);

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz,
              "testKotlinSequencesStateless", "kotlin.sequences.Sequence"));

      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateless$1").isPresent());

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz,
              "testKotlinSequencesStateful", "int", "int", "kotlin.sequences.Sequence"));

      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testKotlinSequencesStateful$1").isPresent());

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testBigExtraMethod"));

      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod$1").isPresent());
      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod2$1").isPresent());
      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethod3$1").isPresent());

      assertEquals(
          Sets.newHashSet(),
          collectAccessedTypes(lambdaCheck, clazz, "testBigExtraMethodReturningLambda"));

      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda$1")
          .isPresent());
      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda2$1")
          .isPresent());
      assertFalse(inspector.clazz(
          "class_inliner_lambda_k_style.MainKt$testBigExtraMethodReturningLambda3$1")
          .isPresent());
    });
  }

  @Test
  public void testDataClass() throws Exception {
    final String mainClassName = "class_inliner_data_class.MainKt";
    runTest("class_inliner_data_class", mainClassName, true, (app) -> {
      DexInspector inspector = new DexInspector(app);
      ClassSubject clazz = inspector.clazz(mainClassName);
      assertTrue(collectAccessedTypes(
          type -> !type.toSourceString().startsWith("java."),
          clazz, "main", String[].class.getCanonicalName()).isEmpty());
      assertEquals(
          Lists.newArrayList(
              "void kotlin.jvm.internal.Intrinsics.throwParameterIsNullException(java.lang.String)"
          ),
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
            .map(insn -> insn.getField().getHolder())
    )
        .filter(isTypeOfInterest)
        .map(DexType::toSourceString)
        .collect(Collectors.toSet());
  }

  protected void runTest(String folder, String mainClass,
      boolean enabled, AndroidAppInspector inspector) throws Exception {
    runTest(
        folder, mainClass, null,
        options -> {
          options.enableInlining = true;
          options.enableClassInlining = enabled;
          options.enableLambdaMerging = false;
        }, inspector);
  }

  private List<String> collectStaticCalls(ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return filterInstructionKind(code, InvokeStatic.class)
        .map(insn -> insn.getMethod().toSourceString())
        .sorted()
        .collect(Collectors.toList());
  }
}
