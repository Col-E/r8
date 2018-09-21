// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class A {

  public void method0() {
    System.out.print(0);
  }

  public void method1(String s) {
    System.out.print(s);
  }

  public void method2(String s1, String s2) {
    System.out.print(s1 + s2);
  }

  public void method3(int i1, int i2) {
    System.out.print(i1 + i2);
  }
}

class Main {
  public static void main(String[] args) throws Exception {
    A a = new A();

    Method m;
    m = A.class.getMethod("method0");
    m.invoke(a);
    m = A.class.getMethod("method1", String.class);
    m.invoke(a, "1");
    m = A.class.getMethod("method2", String.class, String.class);
    m.invoke(a, "2", "3");
    m = A.class.getDeclaredMethod("method0");
    m.invoke(a);
    m = A.class.getDeclaredMethod("method1", String.class);
    m.invoke(a, "1");
    m = A.class.getDeclaredMethod("method2", String.class, String.class);
    m.invoke(a, "2", "3");
    m = A.class.getDeclaredMethod("method3", int.class, int.class);
    m.invoke(a, 2, 2);

    try {
      m = A.class.getMethod("method0");
      m.invoke(a);
      m = A.class.getMethod("method1", String.class);
      m.invoke(a, "1");
      m = A.class.getMethod("method2", String.class, String.class);
      m.invoke(a, "2", "3");
      m = A.class.getDeclaredMethod("method0");
      m.invoke(a);
      m = A.class.getDeclaredMethod("method1", String.class);
      m.invoke(a, "1");
      m = A.class.getDeclaredMethod("method2", String.class, String.class);
      m.invoke(a, "2", "3");
      m = A.class.getDeclaredMethod("method3", int.class, int.class);
      m.invoke(a, 2, 2);
    } catch (Exception e) {
    }

    Class[] argumentTypes;
    argumentTypes = new Class[2];
    argumentTypes[1] = int.class;
    argumentTypes[0] = int.class;
    argumentTypes[0] = String.class;
    argumentTypes[1] = String.class;
    m = A.class.getDeclaredMethod("method2", argumentTypes);
    m.invoke(a, "2", "3");
    m = A.class.getDeclaredMethod("method2", argumentTypes);
    m.invoke(a, "4", "5");

    argumentTypes[1] = int.class;
    argumentTypes[0] = int.class;
    m = A.class.getDeclaredMethod("method3", argumentTypes);
    m.invoke(a, 3, 3);
    m = A.class.getDeclaredMethod("method3", argumentTypes);
    m.invoke(a, 3, 4);

    try {
      argumentTypes = new Class[2];
      argumentTypes[1] = int.class;
      argumentTypes[0] = int.class;
      argumentTypes[0] = String.class;
      argumentTypes[1] = String.class;
      m = A.class.getDeclaredMethod("method2", argumentTypes);
      m.invoke(a, "2", "3");
      m = A.class.getDeclaredMethod("method2", argumentTypes);
      m.invoke(a, "4", "7");

      argumentTypes[1] = int.class;
      argumentTypes[0] = int.class;
      m = A.class.getDeclaredMethod("method3", argumentTypes);
      m.invoke(a, 3, 3);
      m = A.class.getDeclaredMethod("method3", argumentTypes);
      m.invoke(a, 3, 4);
    } catch (Exception e) {
    }
  }
}

@RunWith(Parameterized.class)
public class ReflectionTest extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ReflectionTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    AndroidApp output =
        compileWithR8(
            readClasses(A.class, Main.class), keepMainProguardConfiguration(Main.class), backend);
    CodeInspector inspector = new CodeInspector(output);
    assertThat(inspector.clazz(A.class).method("void", "method0", ImmutableList.of()), isRenamed());
    assertThat(
        inspector.clazz(A.class).method("void", "method1", ImmutableList.of("java.lang.String")),
        isRenamed());
    assertThat(
        inspector
            .clazz(A.class)
            .method("void", "method2", ImmutableList.of("java.lang.String", "java.lang.String")),
        isRenamed());

    assertEquals(runOnJava(Main.class), runOnVM(output, Main.class, backend));
  }
}
