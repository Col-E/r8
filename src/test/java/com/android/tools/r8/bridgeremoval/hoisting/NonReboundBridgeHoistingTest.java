// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.NonReboundBridgeHoistingTestClasses;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonReboundBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonReboundBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addInnerClasses(NonReboundBridgeHoistingTestClasses.class)
        .addProgramClassFileData(
            transformer(C.class).setBridge(C.class.getDeclaredMethod("bridge")).transform())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(NonReboundBridgeHoistingTestClasses.getClassA());
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithOriginalName("m"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithOriginalName("bridge"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(NonReboundBridgeHoistingTestClasses.B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("bridge"), not(isPresent()));

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    assertThat(cClassSubject.uniqueMethodWithOriginalName("bridge"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      new C().bridge();
    }
  }

  @NeverClassInline
  static class C extends NonReboundBridgeHoistingTestClasses.B {

    // The invoke instruction in this bridge cannot be rewritten to target A.m(), since A is not
    // accessible in this context. It therefore points to B.m(), where there is no definition of the
    // method. When the bridge is hoisted to B.m(), the invoke-virtual instruction can be rewritten
    // to target A.m(). This allows hoisting the bridge further from B.m() to A.m().
    @NeverInline
    public /*bridge*/ void bridge() {
      m();
    }
  }
}
