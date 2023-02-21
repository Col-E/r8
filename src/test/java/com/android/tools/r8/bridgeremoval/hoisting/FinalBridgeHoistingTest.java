// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FinalBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FinalBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class, B1.class)
        .addProgramClassFileData(
            transformer(B2.class)
                .setBridge(B2.class.getDeclaredMethod("virtualBridge", Object.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(B1.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
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
    assertThat(aClassSubject.uniqueMethodWithOriginalName("virtualBridge"), isPresent());

    ClassSubject b1ClassSubject = inspector.clazz(B1.class);
    assertThat(b1ClassSubject, isPresent());
    assertThat(b1ClassSubject.uniqueMethodWithOriginalName("virtualBridge"), isPresent());

    ClassSubject b2ClassSubject = inspector.clazz(B2.class);
    assertThat(b2ClassSubject, isPresent());
    assertThat(b2ClassSubject.uniqueMethodWithOriginalName("virtualBridge"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.print(new B1().virtualBridge("Hello"));
      System.out.println(new B2().virtualBridge(" world!"));
    }
  }

  static class A {

    @NeverInline
    public Object m(String arg) {
      return System.currentTimeMillis() >= 0 ? arg : null;
    }
  }

  @NeverClassInline
  static class B1 extends A {

    @KeepConstantArguments
    public String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }

  @NeverClassInline
  static class B2 extends A {

    @KeepConstantArguments
    @NeverInline
    public final /*bridge*/ String virtualBridge(Object o) {
      return (String) m((String) o);
    }
  }
}
