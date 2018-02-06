// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8KotlinIntrinsicsTest extends AbstractR8KotlinTestBase {

  private static final KotlinDataClass KOTLIN_INTRINSICS_CLASS =
      new KotlinDataClass("kotlin.jvm.internal.Intrinsics");

  @Parameters(name = "{0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Boolean.TRUE, Boolean.FALSE);
  }

  public R8KotlinIntrinsicsTest(boolean allowAccessModification) {
    super(allowAccessModification);
  }

  @Test
  public void testParameterNullCheckIsInlined() throws Exception {
    final String extraRules = keepClassMethod("intrinsics.IntrinsicsKt",
        new MethodSignature("expectsNonNullParameters",
            "java.lang.String", Lists.newArrayList("java.lang.String", "java.lang.String")));

    runTest("intrinsics", "intrinsics.IntrinsicsKt", extraRules, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject intrinsicsClass = checkClassExists(
          dexInspector, KOTLIN_INTRINSICS_CLASS.getClassName());

      checkMethodsPresence(intrinsicsClass,
          ImmutableMap.<MethodSignature, Boolean>builder()
              .put(new MethodSignature("throwParameterIsNullException",
                      "void", Collections.singletonList("java.lang.String")),
                  true)
              .put(new MethodSignature("checkParameterIsNotNull",
                      "void", Lists.newArrayList("java.lang.Object", "java.lang.String")),
                  allowAccessModification ? false /* should be inlined*/ : true)
              .build());
    });
  }
}
