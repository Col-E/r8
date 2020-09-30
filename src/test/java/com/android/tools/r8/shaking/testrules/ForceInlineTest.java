// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.testrules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForceInlineTest extends TestBase {

  private TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ForceInlineTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private CodeInspector runTest(List<String> proguardConfiguration) throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class, C.class)
        .addKeepRules(proguardConfiguration)
        .enableNoStaticClassMergingAnnotations()
        .enableProguardTestOptions()
        .compile()
        .inspector();
  }

  @Test
  public void testDefaultInlining() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-keep class **.Main { *; }",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    ClassSubject classA = inspector.clazz(A.class);
    ClassSubject classB = inspector.clazz(B.class);
    ClassSubject classC = inspector.clazz(C.class);
    ClassSubject classMain = inspector.clazz(Main.class);
    assertThat(classA, isPresent());
    assertThat(classB, isPresent());
    assertThat(classC, not(isPresent()));
    assertThat(classMain, isPresent());

    // By default A.m *will not* be inlined (called several times and not small).
    assertThat(classA.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    // By default A.method *will* be inlined (called only once).
    assertThat(classA.method("int", "method", ImmutableList.of()), not(isPresent()));
    // By default B.m *will not* be inlined (called several times and not small).
    assertThat(classB.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    // By default B.method *will* be inlined (called only once).
    assertThat(classB.method("int", "method", ImmutableList.of()), not(isPresent()));
  }

  @Test
  public void testNeverInline() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-neverinline class **.A { method(); }",
                "-neverinline class **.B { method(); }",
                "-keep class **.Main { *; }",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    ClassSubject classA = inspector.clazz(A.class);
    ClassSubject classB = inspector.clazz(B.class);
    ClassSubject classC = inspector.clazz(C.class);
    ClassSubject classMain = inspector.clazz(Main.class);
    assertThat(classA, isPresent());
    assertThat(classB, isPresent());
    assertThat(classC, not(isPresent()));
    assertThat(classMain, isPresent());

    // Compared to the default method is no longer inlined.
    assertThat(classA.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    assertThat(classA.method("int", "method", ImmutableList.of()), isPresent());
    assertThat(classB.method("int", "m", ImmutableList.of("int", "int")), isPresent());
    assertThat(classB.method("int", "method", ImmutableList.of()), isPresent());
  }

  @Test
  public void testForceInline() throws Exception {
    CodeInspector inspector =
        runTest(
            ImmutableList.of(
                "-forceinline class **.A { int m(int, int); }",
                "-forceinline class **.B { int m(int, int); }",
                "-keep class **.Main { *; }",
                "-neverinline class *{ @com.android.tools.r8.NeverInline <methods>;}",
                "-dontobfuscate"));

    // Compared to the default m is now inlined and method still is, so classes A and B are gone.
    assertThat(inspector.clazz(A.class), not(isPresent()));
    assertThat(inspector.clazz(B.class), not(isPresent()));
    assertThat(inspector.clazz(C.class), not(isPresent()));
    assertThat(inspector.clazz(Main.class), isPresent());
  }

  @Test
  public void testForceInlineFails() {
    try {
      runTest(
          ImmutableList.of(
              "-forceinline class **.A { int x(); }",
              "-keep class **.Main { *; }",
              "-dontobfuscate"));
      fail("Force inline of non-inlinable method succeeded");
    } catch (Throwable t) {
      // Ignore assertion error.
    }
  }
}