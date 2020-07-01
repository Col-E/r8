// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Proxy;
import org.junit.Test;

public class IfOnTargetedMethodTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(IfOnTargetedMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if interface * { @" + MyAnnotation.class.getTypeName() + " <methods>; }",
                "-keep,allowobfuscation interface <1>")
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
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
