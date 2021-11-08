// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnusedArgumentsLongTest extends UnusedArgumentsTestBase {

  public UnusedArgumentsLongTest(TestParameters parameters, boolean minification) {
    super(parameters, minification);
  }

  static class TestClass {

    @KeepConstantArguments
    @NeverInline
    public static long a(long a) {
      return a;
    }

    @KeepConstantArguments
    @NeverInline
    public static long a(long a, long b) {
      return a;
    }

    @KeepConstantArguments
    @NeverInline
    public static long a(long a, long b, long c) {
      return a;
    }

    public static void main(String[] args) {
      System.out.print(a(1L));
      System.out.print(a(2L, 3L));
      System.out.print(a(4L, 5L, 6L));
    }
  }

  @Override
  public void configure(R8FullTestBuilder builder) {
    super.configure(builder);
    builder.enableConstantArgumentAnnotations();
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
                      && method.getFinalSignature().parameters[0].equals("long")));
        });
  }
}
