// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PutObjectWithFinalizeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public PutObjectWithFinalizeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(PutObjectWithFinalizeTest.class)
        .addKeepMainRule(TestClass.class)
        // The class staticizer does not consider the finalize() method.
        .addOptionsModification(options -> options.enableClassStaticizer = false)
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(TestClass.class);
              assertThat(classSubject, isPresent());

              MethodSubject mainSubject = classSubject.clinit();
              assertThat(mainSubject, isPresent());

              List<String> presentFields =
                  ImmutableList.of(
                      "directInstanceWithDirectFinalizer",
                      "directInstanceWithIndirectFinalizer",
                      "indirectInstanceWithDirectFinalizer",
                      "indirectInstanceWithIndirectFinalizer",
                      "otherIndirectInstanceWithoutFinalizer",
                      "arrayWithDirectFinalizer",
                      "arrayWithIndirectFinalizer",
                      "otherArrayInstanceWithoutFinalizer");
              for (String name : presentFields) {
                FieldSubject fieldSubject = classSubject.uniqueFieldWithName(name);
                assertThat(fieldSubject, isPresent());
                assertTrue(
                    mainSubject
                        .streamInstructions()
                        .filter(InstructionSubject::isStaticPut)
                        .map(InstructionSubject::getField)
                        .map(field -> field.name.toSourceString())
                        .anyMatch(fieldSubject.getFinalName()::equals));
              }

              List<String> absentFields =
                  ImmutableList.of(
                      "directInstanceWithoutFinalizer",
                      "otherDirectInstanceWithoutFinalizer",
                      "indirectInstanceWithoutFinalizer",
                      "arrayWithoutFinalizer");
              for (String name : absentFields) {
                assertThat(classSubject.uniqueFieldWithName(name), not(isPresent()));
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    static A directInstanceWithDirectFinalizer = new A();
    static A directInstanceWithIndirectFinalizer = new B();
    static Object directInstanceWithoutFinalizer = new C();
    static Object otherDirectInstanceWithoutFinalizer = new Object();

    static A indirectInstanceWithDirectFinalizer = createA();
    static A indirectInstanceWithIndirectFinalizer = createB();
    static Object indirectInstanceWithoutFinalizer = createC();
    static Object otherIndirectInstanceWithoutFinalizer = createObject();

    static A[] arrayWithDirectFinalizer = new A[42];
    static A[] arrayWithIndirectFinalizer = new B[42];
    static Object[] arrayWithoutFinalizer = new C[42];
    static Object[] otherArrayInstanceWithoutFinalizer = new Object[42];

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }

    @NeverInline
    static A createA() {
      return new A();
    }

    @NeverInline
    static B createB() {
      return new B();
    }

    @NeverInline
    static C createC() {
      return new C();
    }

    @NeverInline
    static Object createObject() {
      return new Object();
    }
  }

  @NeverMerge
  static class A {

    @Override
    public void finalize() {
      System.out.println("Finalize!");
    }
  }

  static class B extends A {}

  static class C {}
}
