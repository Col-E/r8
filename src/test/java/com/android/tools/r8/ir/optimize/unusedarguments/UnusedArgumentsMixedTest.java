// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsMixedTest extends UnusedArgumentsTestBase {

  public UnusedArgumentsMixedTest(boolean minification) {
    super(minification);
  }

  @Parameters(name = "minification:{0}")
  public static Collection<Object[]> data() {
    return UnusedArgumentsTestBase.data();
  }

  static class TestClass {
    @NeverInline
    public static int a(int a, Object b) {
      return a;
    }

    @NeverInline
    public static Object a(Object a, int b) {
      return a;
    }

    @NeverInline
    public static int a(int a, Object b, int c) {
      return c;
    }

    @NeverInline
    public static Object a(Object a, int b, Object c) {
      return c;
    }

    public static void main(String[] args) {
      System.out.print(a(1, new Integer(2)));
      System.out.print(a(new Integer(3), 4));
      System.out.print(a(5, new Integer(6), 7));
      System.out.print(a(new Integer(8), 9, new Integer(0)));
    }
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getExpectedResult() {
    return "1370";
  }

  @Override
  public void inspectTestClass(ClassSubject clazz) {
    assertEquals(5, clazz.allMethods().size());
    clazz.forAllMethods(
        method -> {
          Assert.assertTrue(
              method.getFinalName().equals("main")
                  || (method.getFinalSignature().parameters.length == 1
                  && (method.getFinalSignature().parameters[0].equals("int")
                      || method.getFinalSignature().parameters[0].equals("java.lang.Object"))));
        });
  }
}
