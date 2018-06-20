// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class KotlinClassInlinerTest extends AbstractR8KotlinTestBase {
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
    runTest("class_inliner_lambda_j_style", mainClassName, (app) -> {
      DexInspector inspector = new DexInspector(app);
      Predicate<DexType> lambdaCheck = createLambdaCheck(inspector);
      ClassSubject clazz = inspector.clazz(mainClassName);

      assertEquals(
          Sets.newHashSet(),
          collectAccessedLambdaTypes(lambdaCheck, clazz, "testStateless"));

      assertEquals(
          Sets.newHashSet(
              "class_inliner_lambda_j_style.MainKt$testStateful$3"),
          collectAccessedLambdaTypes(lambdaCheck, clazz, "testStateful"));

      assertFalse(
          inspector.clazz("class_inliner_lambda_j_style.MainKt$testStateful$1").isPresent());

      assertEquals(
          Sets.newHashSet(
              "class_inliner_lambda_j_style.MainKt$testStateful2$1"),
          collectAccessedLambdaTypes(lambdaCheck, clazz, "testStateful2"));

      assertEquals(
          Sets.newHashSet(
              "class_inliner_lambda_j_style.MainKt$testStateful3$1"),
          collectAccessedLambdaTypes(lambdaCheck, clazz, "testStateful3"));
    });
  }

  private Set<String> collectAccessedLambdaTypes(
      Predicate<DexType> isLambdaType, ClassSubject clazz, String methodName, String... params) {
    assertNotNull(clazz);
    MethodSignature signature = new MethodSignature(methodName, "void", params);
    DexCode code = clazz.method(signature).getMethod().getCode().asDexCode();
    return Stream.concat(
        filterInstructionKind(code, NewInstance.class)
            .map(insn -> ((NewInstance) insn).getType()),
        filterInstructionKind(code, SgetObject.class)
            .map(insn -> insn.getField().getHolder())
    )
        .filter(isLambdaType)
        .map(DexType::toSourceString)
        .collect(Collectors.toSet());
  }

  @Override
  protected void runTest(String folder, String mainClass,
      AndroidAppInspector inspector) throws Exception {
    runTest(
        folder, mainClass, null,
        options -> {
          options.enableInlining = true;
          options.enableClassInlining = true;
          options.enableLambdaMerging = false;
        }, inspector);
  }
}
