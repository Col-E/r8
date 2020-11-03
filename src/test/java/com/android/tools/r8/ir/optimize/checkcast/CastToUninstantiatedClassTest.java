// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CastToUninstantiatedClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/172194277): Add support for synthetics when generating CF.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public CastToUninstantiatedClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Uninstantiated.class), isAbsent()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Got null", "Caught ClassCastException");
  }

  static class TestClass {

    public static void main(String[] args) {
      testNull();
      testNonNull();
    }

    @NeverInline
    private static void testNull() {
      Uninstantiated u = (Uninstantiated) get(null);
      System.out.println("Got " + u);
    }

    @NeverInline
    private static void testNonNull() {
      try {
        Uninstantiated u = (Uninstantiated) get(new Object());
        System.out.println("Got " + u);
      } catch (ClassCastException e) {
        System.out.println("Caught ClassCastException");
      }
    }

    @NeverInline
    private static Object get(Object o) {
      List<Object> list = new ArrayList<>();
      list.add(o);
      return list.get(0);
    }
  }

  static class Uninstantiated {}
}
