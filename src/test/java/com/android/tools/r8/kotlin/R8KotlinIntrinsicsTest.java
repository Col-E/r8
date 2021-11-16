// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import kotlin.jvm.internal.Intrinsics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinIntrinsicsTest extends AbstractR8KotlinTestBase {

  private static final TestKotlinDataClass KOTLIN_INTRINSICS_CLASS =
      new TestKotlinDataClass("kotlin.jvm.internal.Intrinsics");

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public R8KotlinIntrinsicsTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void testParameterNullCheckIsInlined() throws Exception {
    final String extraRules = keepClassMethod("intrinsics.IntrinsicsKt",
        new MethodSignature("expectsNonNullParameters",
            "java.lang.String", Lists.newArrayList("java.lang.String", "java.lang.String")));
    runTest(
            "intrinsics",
            "intrinsics.IntrinsicsKt",
            testBuilder ->
                testBuilder.addKeepRules(extraRules).noHorizontalClassMerging(Intrinsics.class))
        .inspect(
            inspector -> {
              ClassSubject intrinsicsClass =
                  checkClassIsKept(inspector, KOTLIN_INTRINSICS_CLASS.getClassName());
              checkMethodsPresence(
                  intrinsicsClass,
                  ImmutableMap.<MethodSignature, Boolean>builder()
                      .put(
                          new MethodSignature(
                              "throwParameterIsNullException",
                              "void",
                              Collections.singletonList("java.lang.String")),
                          false)
                      .put(
                          new MethodSignature(
                              "throwParameterIsNullNPE",
                              "void",
                              Collections.singletonList("java.lang.String")),
                          false)
                      .put(
                          new MethodSignature(
                              "checkParameterIsNotNull",
                              "void",
                              Lists.newArrayList("java.lang.Object", "java.lang.String")),
                          kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72))
                      .put(
                          new MethodSignature(
                              "checkNotNullParameter",
                              "void",
                              Lists.newArrayList("java.lang.Object", "java.lang.String")),
                          !kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72))
                      .build());
            });
  }
}
