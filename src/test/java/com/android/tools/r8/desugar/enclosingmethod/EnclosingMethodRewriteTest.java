// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.enclosingmethod;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

interface A {
  default int def() {
    return new C() {
      @Override
      int number() {
        Class<?> clazz = getClass();
        System.out.println(clazz.getEnclosingClass());
        System.out.println(clazz.getEnclosingMethod());
        return 42;
      }
    }.getNumber();
  }
}

abstract class C {
  abstract int number();

  public int getNumber() {
    return number();
  }
}

class TestClass implements A {
  public static void main(String[] args) throws NoClassDefFoundError {
    System.out.println(new TestClass().def());
  }
}

@RunWith(Parameterized.class)
public class EnclosingMethodRewriteTest extends TestBase {

  private static final Class<?> MAIN = TestClass.class;
  private final TestParameters parameters;

  private final String[] EXPECTED =
      new String[] {
        "interface " + A.class.getTypeName(),
        "public default int " + A.class.getTypeName() + ".def()",
        "42"
      };

  private final String[] EXPECTED_CC =
      new String[] {
        "class " + A.class.getTypeName() + "$-CC",
        "public static int " + A.class.getTypeName() + "$-CC.a(" + A.class.getTypeName() + ")",
        "42"
      };

  private final String[] EXPECTED_NOUGAT =
      new String[] {
        "interface " + A.class.getTypeName(), "public int " + A.class.getTypeName() + ".def()", "42"
      };

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnclosingMethodRewriteTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClassesAndInnerClasses(A.class)
        .addProgramClasses(C.class, MAIN)
        .addKeepAllClassesRule()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            result -> {
              if (!parameters.isCfRuntime()
                  && parameters.getApiLevel().isEqualTo(AndroidApiLevel.N)) {
                result.assertSuccessWithOutputLines(EXPECTED_NOUGAT);
              } else {
                result.assertSuccessWithOutputLines(EXPECTED);
              }
            },
            result -> result.assertSuccessWithOutputLines(EXPECTED_CC));
  }

  @Test
  public void testR8Full() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(A.class)
        .addProgramClasses(C.class, MAIN)
        .addKeepAllClassesRule()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            result -> {
              if (!parameters.isCfRuntime()
                  && parameters.getApiLevel().isEqualTo(AndroidApiLevel.N)) {
                result.assertSuccessWithOutputLines(EXPECTED_NOUGAT);
              } else {
                result.assertSuccessWithOutputLines(EXPECTED);
              }
            },
            result -> result.assertSuccessWithOutputLines(EXPECTED_CC));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject cImplSubject = inspector.clazz(A.class.getTypeName() + "$1");
    assertThat(cImplSubject, isPresent());
    ClassSubject enclosingClassSubject =
        parameters.canUseDefaultAndStaticInterfaceMethods()
            ? inspector.clazz(A.class.getTypeName())
            : inspector.clazz(A.class.getTypeName()).toCompanionClass();
    assertThat(enclosingClassSubject, isPresent());
    EnclosingMethodAttribute enclosingMethodAttribute =
        cImplSubject.getDexProgramClass().getEnclosingMethodAttribute();
    assertEquals(
        enclosingClassSubject.getDexProgramClass().getType(),
        enclosingMethodAttribute.getEnclosingMethod().getHolderType());
  }
}
