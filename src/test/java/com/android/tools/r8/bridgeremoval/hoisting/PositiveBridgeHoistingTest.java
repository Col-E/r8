// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PositiveBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PositiveBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class, B3.class, B4.class)
        .addProgramClassFileData(
            transformer(B1.class)
                .setBridge(B1.class.getDeclaredMethod("superBridge", Object.class))
                .setBridge(B1.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform(),
            transformer(B2.class)
                .setBridge(B2.class.getDeclaredMethod("superBridge", Object.class))
                .setBridge(B2.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform(),
            transformer(B5.class)
                .setBridge(B5.class.getDeclaredMethod("superBridge", Object.class))
                .setBridge(B5.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("m"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("superBridge"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("virtualBridge"), isPresent());

    ClassSubject b1ClassSubject = inspector.clazz(B1.class);
    assertThat(b1ClassSubject, isPresent());
    assertThat(b1ClassSubject.uniqueMethodWithName("superBridge"), not(isPresent()));
    assertThat(b1ClassSubject.uniqueMethodWithName("virtualBridge"), not(isPresent()));

    ClassSubject b2ClassSubject = inspector.clazz(B2.class);
    assertThat(b2ClassSubject, isPresent());
    assertThat(b2ClassSubject.uniqueMethodWithName("superBridge"), not(isPresent()));
    assertThat(b2ClassSubject.uniqueMethodWithName("virtualBridge"), not(isPresent()));

    ClassSubject b4ClassSubject = inspector.clazz(B4.class);
    assertThat(b4ClassSubject, isPresent());
    assertThat(b4ClassSubject.uniqueMethodWithName("superBridge"), isPresent());
    assertThat(b4ClassSubject.uniqueMethodWithName("virtualBridge"), isPresent());

    ClassSubject b5ClassSubject = inspector.clazz(B5.class);
    assertThat(b5ClassSubject, isPresent());
    assertThat(b5ClassSubject.uniqueMethodWithName("superBridge"), isPresent());
    assertThat(b5ClassSubject.uniqueMethodWithName("virtualBridge"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.print(new B1().superBridge("Hel"));
      System.out.print(new B1().virtualBridge("lo"));
      System.out.print(new B2().superBridge(" "));
      System.out.print(new B2().virtualBridge("w"));
      System.out.print(new B3().m("o"));
      System.out.print(new B4().superBridge("r"));
      System.out.print(new B4().virtualBridge("l"));
      System.out.print(new B5().superBridge("d"));
      System.out.println(new B5().virtualBridge("!"));
    }
  }

  static class A {

    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }

    @NeverInline
    public Object m2(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }
  }

  @NeverClassInline
  static class B1 extends A {

    // This bridge can be hoisted to A if the invoke-super instruction is rewritten to an
    // invoke-virtual instruction.
    @NeverInline
    public /*bridge*/ String superBridge(Object o) {
      return (String) super.m((String) o);
    }

    // This bridge can be hoisted to A.
    @NeverInline
    public /*bridge*/ String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B2 extends A {

    // By hoisting B1.superBridge() to A this method bridge redundant.
    @NeverInline
    public /*bridge*/ String superBridge(Object o) {
      return (String) super.m((String) o);
    }

    // By hoisting B1.virtualBridge() to A this method bridge redundant.
    @NeverInline
    public /*bridge*/ String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }

  // The fact that this class does not declare superBridge() or virtualBridge() should not prevent
  // us from hoisting these bridges from B1/B2 to A.
  //
  // Strictly speaking, there could be an invoke-virtual instruction that targets B3.virtualBridge()
  // and fails with a NoSuchMethodError in the input program. After hoisting B1.virtualBridge() to A
  // such an instruction would no longer fail with a NoSuchMethodError in the generated program.
  //
  // If this ever turns out be an issue, it would be possible to track if there is an invoke
  // instruction targeting B3.virtualBridge() that fails with a NoSuchMethodError in the Enqueuer,
  // but this should never be the case in practice.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class B3 extends A {}

  // The fact that this class declares superBridge() and virtualBridge() should not prevent
  // us from hoisting other bridges to A.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class B4 extends A {

    @NeverInline
    public String superBridge(Object o) {
      return System.currentTimeMillis() >= 0 ? ((String) o) : null;
    }

    @NeverInline
    public String virtualBridge(Object o) {
      return System.currentTimeMillis() >= 0 ? ((String) o) : null;
    }
  }

  // This class declares the same bridges, but with different (bridge) behavior. They are candidates
  // for hoisting, but will not be hoisted because it is better to hoist the bridges declared on B1.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class B5 extends A {

    @NeverInline
    public /*bridge*/ String superBridge(Object o) {
      return (String) super.m2((String) o);
    }

    @NeverInline
    public /*bridge*/ String virtualBridge(Object o) {
      return (String) m2((String) o);
    }
  }
}
