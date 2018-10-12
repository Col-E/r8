// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {
  int x = 1;
  int a() throws ClassNotFoundException {
    // Class D is expected to be kept - vertical class merging or not. The -if rule say that if
    // the method A.a is in the output, then class D is needed.
    String p = getClass().getPackage().getName();
    Class.forName(p + ".D");
    return 4;
  }
}

class B extends A {
  int y = 2;
  int b() {
    return 5;
  }
}

class C extends B {
  int z = 3;
  int c() {
    return 6;
  }
}

class D {
}

class Main {
  public static void main(String[] args) throws ClassNotFoundException {
    C c = new C();
    System.out.print("" + c.x + "" + c.y + "" + c.z + "" + c.a() + "" + c.b() + "" + c.c());
  }
}

@RunWith(Parameterized.class)
public class IfRuleWithVerticalClassMerging extends TestBase {

  private static final List<Class> CLASSES =
      ImmutableList.of(A.class, B.class, C.class, D.class, Main.class);

  private final Backend backend;
  private final boolean enableVerticalClassMerging;

  public IfRuleWithVerticalClassMerging(Backend backend, boolean enableVerticalClassMerging) {
    this.backend = backend;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  @Parameters(name = "Backend: {0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as Proguard does not merge A into B.
    return ImmutableList.of(
        new Object[] {Backend.DEX, true},
        new Object[] {Backend.DEX, false},
        new Object[] {Backend.CF, true},
        new Object[] {Backend.CF, false});
  }

  private void configure(InternalOptions options) {
    options.enableVerticalClassMerging = enableVerticalClassMerging;

    // TODO(b/110148109): Allow ordinary method inlining when -if rules work with inlining.
    options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
  }

  private void check(AndroidApp app) throws Exception {
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazzA = inspector.clazz(A.class);
    assertEquals(!enableVerticalClassMerging, clazzA.isPresent());
    ClassSubject clazzB = inspector.clazz(B.class);
    assertThat(clazzB, isPresent());
    ClassSubject clazzD = inspector.clazz(D.class);
    assertThat(clazzD, isPresent());
    assertEquals("123456", runOnVM(app, Main.class, backend));
  }

  @Test
  public void testMergedClassInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    String config =
        String.join(
            System.lineSeparator(),
            "-keep class **.Main { public static void main(java.lang.String[]); }",
            "-keep class **.C",
            "-if class **.A",
            "-keep class **.D",
            "-dontobfuscate");
    check(compileWithR8(readClasses(CLASSES), config, this::configure, backend));
  }

  @Test
  public void testMergedClassFieldInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    // Main.main access A.x, so that field exists satisfying the if rule.
    String config =
        String.join(
            System.lineSeparator(),
            "-keep class **.Main { public static void main(java.lang.String[]); }",
            "-keep class **.C",
            "-if class **.A { int x; }",
            "-keep class **.D",
            "-dontobfuscate");
    check(compileWithR8(readClasses(CLASSES), config, this::configure, backend));
  }

  @Test
  public void testMergedClassMethodInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    // Main.main access A.a(), that method exists satisfying the if rule.
    String config =
        String.join(
            System.lineSeparator(),
            "-keep class **.Main { public static void main(java.lang.String[]); }",
            "-keep class **.C",
            "-if class **.A { int a(); }",
            "-keep class **.D",
            "-dontobfuscate");
    check(compileWithR8(readClasses(CLASSES), config, this::configure, backend));
  }
}
