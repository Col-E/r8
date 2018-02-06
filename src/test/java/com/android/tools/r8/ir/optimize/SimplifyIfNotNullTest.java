// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Format21t;
import com.android.tools.r8.code.Format22t;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SimplifyIfNotNullTest extends AsmTestBase {
  private static boolean isIf(Instruction instruction) {
    return instruction instanceof Format21t || instruction instanceof Format22t;
  }

  private void buildAndTest(Class<?> testClass, List<MethodSignature> signatures) throws Exception {
    AndroidApp app = buildAndroidApp(ToolHelper.getClassAsBytes(testClass));
    AndroidApp r8Result = compileWithR8(
        app, keepMainProguardConfiguration(testClass), o -> o.inlineAccessors = false);
    DexInspector dexInspector = new DexInspector(r8Result);
    for (MethodSignature signature : signatures) {
      DexEncodedMethod method =
          dexInspector.clazz(testClass.getName()).method(signature).getMethod();
      long count = Arrays.stream(method.getCode().asDexCode().instructions)
          .filter(SimplifyIfNotNullTest::isIf).count();
      assertEquals(0, count);
    }
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    buildAndTest(NonNullAfterInvoke.class, ImmutableList.of(foo, bar));
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String[]"});
    buildAndTest(NonNullAfterArrayAccess.class, ImmutableList.of(foo, bar));
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    MethodSignature foo = new MethodSignature("foo", "int",
        new String[]{"com.android.tools.r8.ir.nonnull.FieldAccessTest"});
    MethodSignature bar = new MethodSignature("bar", "int",
        new String[]{"com.android.tools.r8.ir.nonnull.FieldAccessTest"});
    MethodSignature foo2 = new MethodSignature("foo2", "int",
        new String[]{"com.android.tools.r8.ir.nonnull.FieldAccessTest"});
    buildAndTest(NonNullAfterFieldAccess.class, ImmutableList.of(foo, bar, foo2));
  }
}
