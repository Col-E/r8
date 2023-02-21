// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.desugar.interfacemethods;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BridgeInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep interface " + I.class.getTypeName() + " { m(); }")
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I::m")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector codeInspector) {
    ClassSubject c = codeInspector.clazz(C.class);
    assertThat(c, isPresent());
    MethodSubject m = c.uniqueMethodWithOriginalName("m");
    assertThat(m, isPresent());
    assertTrue(m.getMethod().hasCode());
    // TODO(b/124017330): Verify that I$-CC.m() has been inlined into C.m().
    //assertTrue(
    //    m.iterateInstructions(i -> i.isConstString("I::m", JumboStringMode.ALLOW)).hasNext());
    //codeInspector.forAllClasses(classSubject -> {
    //  assertFalse(classSubject.getOriginalDescriptor().contains("$-CC"));
    //});
  }

  static class Main {
    public static void main(String[] args) throws Exception {
      C obj = new C();
      for (Method m : obj.getClass().getDeclaredMethods()) {
        m.invoke(obj);
      }
    }
  }

  @NoVerticalClassMerging
  interface I {
    default void m() {
      System.out.println("I::m");
    }
  }

  static class C implements I {}
}
