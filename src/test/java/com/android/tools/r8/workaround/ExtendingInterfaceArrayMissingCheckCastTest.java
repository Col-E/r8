// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// This is a reproduction of b/188112948.
public class ExtendingInterfaceArrayMissingCheckCastTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Hello World!"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ExtendingInterfaceArrayMissingCheckCastTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntime(),
            // TODO(b/188112948): This should not throw a verification error.
            result -> result.assertFailureWithErrorThatThrows(VerifyError.class),
            result -> result.assertSuccessWithOutputLines(EXPECTED));
  }

  public static class Utility {

    public static <T extends Base<T> & I> void get(T enumType) {
      // JDK 8 will add a checkcast [I such that the [Base array is cast to the correct type.
      // JDK 9 and later will not add the checkcast.
      tryGet(enumType.getParams());
    }

    // this will become void tryGet(I[] value) when compiled due to the extends.
    public static <T extends I> void tryGet(T[] value) {
      System.out.println("Hello World!");
    }
  }

  public interface I {
    void call();
  }

  public static class Base<T extends Base<T>> {

    T[] getParams() {
      return null;
    }
  }

  public static class Foo extends Base<Foo> implements I {

    @Override
    public void call() {
      System.out.println("I::Foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Utility.get(new Foo());
    }
  }
}
