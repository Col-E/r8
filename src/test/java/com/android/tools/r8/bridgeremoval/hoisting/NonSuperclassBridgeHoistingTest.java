// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonSuperclassBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonSuperclassBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(
            transformer(B.class)
                .setBridge(B.class.getDeclaredMethod("bridge", Object.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with signature changes.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertFalse(aClassSubject.getDexProgramClass().getMethodCollection().hasVirtualMethods());

    ClassSubject b1ClassSubject = inspector.clazz(B.class);
    assertThat(b1ClassSubject, isPresent());
    assertThat(b1ClassSubject.uniqueMethodWithOriginalName("m"), isPresent());
    assertThat(b1ClassSubject.uniqueMethodWithOriginalName("bridge"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new B().bridge("Hello world!"));
    }
  }

  @NoVerticalClassMerging
  static class A {}

  @NeverClassInline
  static class B extends A {

    @KeepConstantArguments
    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }

    // This bridge cannot be hoisted to A, since it targets a method on the enclosing class.
    // Hoisting the bridge to A would lead to a NoSuchMethodError.
    @KeepConstantArguments
    @NeverInline
    public /*bridge*/ String bridge(Object o) {
      return (String) m((String) o);
    }
  }
}
