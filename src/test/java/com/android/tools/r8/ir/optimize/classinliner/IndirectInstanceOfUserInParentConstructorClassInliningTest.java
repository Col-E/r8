// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndirectInstanceOfUserInParentConstructorClassInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .setMinApi(parameters.getApiLevel())
                .compile());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new B().get());
    }
  }

  @NoVerticalClassMerging
  static class A {

    A() {
      if (this instanceof B) {
        System.out.print("Hello");
      } else {
        throw new RuntimeException();
      }
    }
  }

  static class B extends A {

    @NeverInline
    String get() {
      return System.currentTimeMillis() > 0 ? ", world!" : null;
    }
  }
}
