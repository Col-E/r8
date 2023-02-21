// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CovariantReturnTypeInSubInterfaceTest extends TestBase {

  interface SuperInterface {
    Super foo();
  }

  interface SubInterface extends SuperInterface {
    @Override
    Sub foo();
  }

  static class Super {
    protected int bar() {
      return 0;
    }
  }

  static class Sub extends Super {
    @Override
    protected int bar() {
      return 1;
    }
  }

  static class SuperImplementer implements SuperInterface {
    @Override
    public Super foo() {
      return new Sub();
    }
  }

  static class SubImplementer extends SuperImplementer implements SubInterface {
    @Override
    public Sub foo() {
      return (Sub) super.foo();
    }
  }

  static class TestMain {
    public static void main(String[] args) {
      SubImplementer subImplementer = new SubImplementer();
      Super sup = subImplementer.foo();
      System.out.print(sup.bar());
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CovariantReturnTypeInSubInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CovariantReturnTypeInSubInterfaceTest.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestMain.class)
        .addKeepRules(
            "-keep,allowobfuscation class **.*$Super* { <methods>; }",
            "-keep,allowobfuscation class **.*$Sub* { <methods>; }")
        .addOptionsModification(
            internalOptions -> internalOptions.inlinerOptions().enableInlining = false)
        .run(parameters.getRuntime(), TestMain.class)
        .inspect(inspector -> inspect(inspector));
  }

  private void inspect(CodeInspector inspector) throws NoSuchMethodException {
    ClassSubject superInterface = inspector.clazz(SuperInterface.class);
    assertThat(superInterface, isPresentAndRenamed());
    MethodSubject foo1 = superInterface.uniqueMethodWithOriginalName("foo");
    assertThat(foo1, isPresentAndRenamed());
    ClassSubject subInterface = inspector.clazz(SubInterface.class);
    assertThat(subInterface, isPresentAndRenamed());
    MethodSubject foo2 = subInterface.method(SubInterface.class.getDeclaredMethod("foo"));
    assertThat(foo2, isPresentAndRenamed());
    assertEquals(foo1.getFinalName(), foo2.getFinalName());
  }

  @Test
  public void test_notAggressively() throws Exception {
    test();
  }
}
