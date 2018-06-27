// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

import java.lang.reflect.Method;

public class PinnedParameterTypesTest {

  public static void main(String[] args) throws Exception {
    for (Method method : TestClass.class.getMethods()) {
      if (method.getName().equals("method")) {
        Class<?> parameterType = method.getParameterTypes()[0];

        // Should print classmerging.PinnedParameterTypesTest$Interface when -keepparameternames is
        // used.
        System.out.println(parameterType.getName());

        method.invoke(null, new InterfaceImpl());
        break;
      }
    }
  }

  public interface Interface {

    void foo();
  }

  public static class InterfaceImpl implements Interface {

    @Override
    public void foo() {
      System.out.println("In InterfaceImpl.foo()");
    }
  }

  public static class TestClass {

    // This method has been kept explicitly by a keep rule. Therefore, since -keepparameternames is
    // used, Interface must not be merged into InterfaceImpl.
    public static void method(Interface obj) {
      obj.foo();
    }
  }
}
