// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureReflectiveInnerTest extends TestBase {

  private final String EXPECTED = "S";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureReflectiveInnerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepAttributeSignature()
        .addKeepClassRules(Foo.Bar.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Foo.class), isPresent());
              assertThat(inspector.clazz(Foo.Bar.class), isPresent());
            });
  }

  @NeverClassInline
  public static class Foo<T> {

    public class Bar<S> {

      public void test() {
        Class<? extends Bar> aClass = (Class<? extends Bar>) this.getClass();
        for (TypeVariable<? extends Class<? extends Bar>> typeParameter :
            aClass.getTypeParameters()) {
          System.out.println(typeParameter);
        }
      }
    }

    @NeverInline
    public Bar<String> foo() {
      return new Bar<>();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Foo<Integer>.Bar<String> foo = new Foo<Integer>().foo();
      foo.test();
    }
  }
}
