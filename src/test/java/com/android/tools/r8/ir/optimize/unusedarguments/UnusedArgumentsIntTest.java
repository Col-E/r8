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
public class UnusedArgumentsIntTest extends UnusedArgumentsTestBase {

  public UnusedArgumentsIntTest(boolean minification) {
    super(minification);
  }

  @Parameters(name = "minification:{0}")
  public static Collection<Object[]> data() {
    return UnusedArgumentsTestBase.data();
  }

  static class TestClass {

    @NeverInline
    public static int a(int a) {
      return a;
    }

    @NeverInline
    public static int a(int a, int b) {
      return a;
    }

    @NeverInline
    public static int a(int a, int b, int c) {
      return a;
    }

    public static void main(String[] args) {
      System.out.print(a(1));
      System.out.print(a(2, 3));
      System.out.print(a(4, 5, 6));
    }
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getExpectedResult() {
    return "124";
  }

  @Override
  public void inspectTestClass(ClassSubject clazz) {
    assertEquals(4, clazz.allMethods().size());
    clazz.forAllMethods(
        method -> {
          Assert.assertTrue(
              method.getFinalName().equals("main")
                  || (method.getFinalSignature().parameters.length == 1
                      && method.getFinalSignature().parameters[0].equals("int")));
        });
  }
}
