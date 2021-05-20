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
public class ExtendingInterfaceArrayCheckCastTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Hello World!"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ExtendingInterfaceArrayCheckCastTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Utility {

    public static <T extends Base<T> & I> void get(T enumType) {
      // If using I[] as the local variable type, the compiler will automatically insert a checkcast
      // and therefore circumvent b/188112948. Another option is to explicitly insert a checkcast.
      I[] params = enumType.getParams();
      tryGet(params);
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
