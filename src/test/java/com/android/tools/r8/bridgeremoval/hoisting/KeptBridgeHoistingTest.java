// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class KeptBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeptBridgeHoistingTest(TestParameters parameters) {
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
        .addKeepRules("-keep class " + B.class.getTypeName() + " { java.lang.String bridge(...); }")
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
    assertThat(aClassSubject.uniqueMethodWithOriginalName("m"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("bridge"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new B().bridge("Hello world!"));
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }
  }

  @NeverClassInline
  static class B extends A {

    // This bridge is kept by a Proguard configuration rule and must therefore not be hoisted to A.
    @NeverInline
    public /*bridge*/ String bridge(Object o) {
      return (String) m((String) o);
    }
  }
}
