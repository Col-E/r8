// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.desugar.interfacemethods;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;

@NoVerticalClassMerging
interface I {
  default void m() {
    System.out.println("I::m");
  }
}

class C implements I {
}

class BridgeInliningTestRunner {
  public static void main(String[] args) throws Exception {
    C obj = new C();
    for (Method m : obj.getClass().getDeclaredMethods()) {
      m.invoke(obj);
    }
  }
}

public class BridgeInliningTest extends TestBase {
  private static final Class<?> MAIN = BridgeInliningTestRunner.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("I::m");

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(I.class, C.class, MAIN)
        .setMinApi(AndroidApiLevel.L)
        .enableNoVerticalClassMergingAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep interface **.I { m(); }")
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector codeInspector) {
    ClassSubject c = codeInspector.clazz(C.class);
    assertThat(c, isPresent());
    MethodSubject m = c.uniqueMethodWithName("m");
    assertThat(m, isPresent());
    assertTrue(m.getMethod().hasCode());
    // TODO(b/124017330): Verify that I$-CC.m() has been inlined into C.m().
    //assertTrue(
    //    m.iterateInstructions(i -> i.isConstString("I::m", JumboStringMode.ALLOW)).hasNext());
    //codeInspector.forAllClasses(classSubject -> {
    //  assertFalse(classSubject.getOriginalDescriptor().contains("$-CC"));
    //});
  }

}
