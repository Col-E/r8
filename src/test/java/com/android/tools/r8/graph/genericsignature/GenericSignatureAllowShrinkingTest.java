// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.ParameterizedType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// This is a reproduction of b/189443104.
public class GenericSignatureAllowShrinkingTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"class java.lang.String"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureAllowShrinkingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8WithDirectKeep() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassRules(Foo.class)
        .addKeepRules("-keep class * extends " + Foo.class.getTypeName() + " { *; }")
        .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8AllowShrinking() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepRules("-keep,allowshrinking class " + Foo.class.getTypeName() + " { *; }")
        .addKeepRules(
            "-keep,allowshrinking,allowobfuscation class * extends "
                + Foo.class.getTypeName()
                + " { *; }")
        .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject foo = inspector.clazz(Foo.class);
    assertThat(foo, isPresent());
    assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Object;", foo.getFinalSignatureAttribute());

    ClassSubject main$1 = inspector.clazz(Main.class.getTypeName() + "$1");
    assertThat(main$1, isPresent());
    assertEquals(
        "L" + binaryName(Foo.class) + "<Ljava/lang/String;>;", main$1.getFinalSignatureAttribute());
  }

  public static class Foo<T> {

    public void print() {
      ParameterizedType genericSuperclass =
          (ParameterizedType) this.getClass().getGenericSuperclass();
      System.out.println(genericSuperclass.getActualTypeArguments()[0].toString());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      (new Foo<String>() {}).print();
    }
  }
}
