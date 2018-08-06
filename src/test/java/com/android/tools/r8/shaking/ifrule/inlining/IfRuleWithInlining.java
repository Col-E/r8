// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.inlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {
  static public int a() {
    try {
      String p = A.class.getPackage().getName();
      Class.forName(p + ".D");
    } catch (ClassNotFoundException e) {
      return 2;
    }
    return 1;
  }
}

class D {
}

class Main {
  public static void main(String[] args) {
    System.out.print("" + A.a());
  }
}

@RunWith(Parameterized.class)
public class IfRuleWithInlining extends ProguardCompatibilityTestBase {
  private final static List<Class> CLASSES = ImmutableList.of(
      A.class, D.class, Main.class);

  private final Shrinker shrinker;
  private final boolean inlineMethod;

  public IfRuleWithInlining(Shrinker shrinker, boolean inlineMethod) {
    this.shrinker = shrinker;
    this.inlineMethod = inlineMethod;
  }

  @Parameters(name = "shrinker: {0} inlineMethod: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as triggering inlining in Proguard is out of our control.
    return ImmutableList.of(
        new Object[]{Shrinker.R8, true},
        new Object[]{Shrinker.R8, false}
    );
  }

  private void check(AndroidApp app) throws Exception {
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazzA = inspector.clazz(A.class);
    assertThat(clazzA, isPresent());
    // A.a might be inlined.
    assertEquals(!inlineMethod, clazzA.method("int", "a", ImmutableList.of()).isPresent());
    // TODO(110148109): class D should be present - inlining or not.
    assertEquals(!inlineMethod, inspector.clazz(D.class).isPresent());
    ProcessResult result = runOnArtRaw(app, Main.class.getName());
    assertEquals(0, result.exitCode);
    // TODO(110148109): Output should be the same - inlining or not.
    assertEquals(!inlineMethod ? "1" : "2", result.stdout);
  }

  @Test
  public void testMergedClassMethodInIfRule() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.Main { public static void main(java.lang.String[]); }",
        inlineMethod
            ? "-forceinline class **.A { int a(); }"
            : "-neverinline class **.A { int a(); }",
        "-if class **.A { static int a(); }",
        "-keep class **.D",
        "-dontobfuscate"
    );

    check(runShrinker(shrinker, CLASSES, config));
  }
}
