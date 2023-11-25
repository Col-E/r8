// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@NeverClassInline
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

@NeverClassInline
class B extends A {
  int y = 2;
  int b() {
    return 5;
  }
}

@NeverClassInline
class C extends B {
  int z = 3;
  int c() {
    return 6;
  }
}

@NeverClassInline
class D {}

class Main {
  public static void main(String[] args) throws ClassNotFoundException {
    C c = new C();
    System.out.print("" + c.x + "" + c.y + "" + c.z + "" + c.a() + "" + c.b() + "" + c.c());
  }
}

@RunWith(Parameterized.class)
public class IfRuleWithVerticalClassMerging extends TestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(A.class, B.class, C.class, D.class, Main.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameters(name = "{0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as Proguard does not merge A into B.
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private void configure(InternalOptions options) {
    options.getVerticalClassMergerOptions().setEnabled(enableVerticalClassMerging);
    // TODO(b/141093535): The precondition set for conditionals is currently based on the syntactic
    // form, when merging is enabled, if the precondition is merged to a differently named type, the
    // rule will still fire, but the reported precondition type is incorrect.
    options.testing.verifyKeptGraphInfo = !enableVerticalClassMerging;
  }

  @Test
  public void testMergedClassInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    runTestWithProguardConfig(
        StringUtils.lines(
            "-keep class **.C", "-if class **.A", "-keep class **.D", "-dontobfuscate"));
  }

  @Test
  public void testMergedClassFieldInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    // Main.main access A.x, so that field exists satisfying the if rule.
    runTestWithProguardConfig(
        StringUtils.lines(
            "-keep class **.C", "-if class **.A { int x; }", "-keep class **.D", "-dontobfuscate"));
  }

  @Test
  public void testMergedClassMethodInIfRule() throws Exception {
    // Class C is kept, meaning that it will not be touched.
    // Class A will be merged into class B.
    // Main.main access A.a(), that method exists satisfying the if rule.
    runTestWithProguardConfig(
        StringUtils.lines(
            "-keep class **.C",
            "-if class **.A { int a(); }",
            "-keep class **.D",
            "-dontobfuscate"));
  }

  private void runTestWithProguardConfig(String config) throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSES)
            .addKeepMainRule(Main.class)
            .addKeepRules(config)
            .enableNeverClassInliningAnnotations()
            .addOptionsModification(this::configure)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutput("123456")
            .inspector();

    ClassSubject clazzA = inspector.clazz(A.class);
    assertEquals(!enableVerticalClassMerging, clazzA.isPresent());
    ClassSubject clazzB = inspector.clazz(B.class);
    assertThat(clazzB, isPresent());
    ClassSubject clazzD = inspector.clazz(D.class);
    assertThat(clazzD, isPresent());
  }
}
