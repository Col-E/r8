// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnTargetedMethodTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello world!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addInnerClasses(IfOnTargetedMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if interface * { @" + MyAnnotation.class.getTypeName() + " <methods>; }",
                "-keep,allowobfuscation interface <1>")
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject interfaceSubject = inspector.clazz(Interface.class);
    assertThat(interfaceSubject, isPresentAndRenamed());
  }

  static class TestClass {

    public static void main(String[] args) {
      Interface obj = getInstance();
      obj.method();
    }

    static Interface getInstance() {
      return (Interface)
          Proxy.newProxyInstance(
              Interface.class.getClassLoader(),
              new Class[] {Interface.class},
              (o, method, objects) -> {
                System.out.println("Hello world!");
                return null;
              });
    }
  }

  interface Interface {

    @MyAnnotation
    void method();
  }

  @interface MyAnnotation {}
}
