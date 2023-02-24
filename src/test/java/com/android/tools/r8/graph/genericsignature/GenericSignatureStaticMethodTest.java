// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureStaticMethodTest extends TestBase {

  private final String[] EXPECTED =
      new String[] {
        "true",
        "public class com.android.tools.r8.graph.genericsignature"
            + ".GenericSignatureStaticMethodTest$Main<T>"
      };
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureStaticMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setAccessFlags(Main.class.getDeclaredMethod("test"), AccessFlags::setStatic)
                .transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .setAccessFlags(Main.class.getDeclaredMethod("test"), AccessFlags::setStatic)
                .transform())
        .addKeepAllClassesRule()
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Main<T> {

    public static void main(String[] args) throws Exception {
      Method test = Main.class.getDeclaredMethod("test");
      System.out.println(Modifier.isStatic(test.getModifiers()));
      Type genericReturnType = test.getGenericReturnType();
      if (genericReturnType instanceof TypeVariable) {
        Class<?> genericDeclaration =
            (Class<?>) ((TypeVariable<?>) genericReturnType).getGenericDeclaration();
        System.out.println(genericDeclaration.toGenericString());
      }
    }

    public /* static after rewriting */ T test() {
      return null;
    }
  }
}
