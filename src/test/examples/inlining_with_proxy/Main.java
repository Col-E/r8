// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package inlining_with_proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Main {

  public static void main(String[] args) {
    // We need to keep Bar so that Foo has a single implementation class.
    Bar bar = new Bar();
    Object proxy = createProxyOfFoo(bar);

    Foo foo = (Foo) proxy;

    // 1st call to foo: since we don't know if foo is null or not, we cannot inline this call.
    foo.foo();

    // Insert some code to make sure both invokes of foo are in separate basic blocks.
    // TODO(shertz) this should no longer be required when our type analysis manages invokes in the
    // same block.
    if (args != null && args.length > 0) {
      System.out.println(args[0]);
    } else {
      System.out.println("No args");
    }

    // 2nd call to foo: at this point we know that it is non-null (otherwise the previous call would
    // have thrown a NPE and leaves this method). However we do not know the exact type of foo,
    // despite of class Bar being the only implementation of Foo at compilation time. Indeed, the
    // actual receiver type is the Proxy class we created above.
    foo.foo();

    // We insert a 3rd call so that the 'double-inlining' condition allows the inlining of the
    // 2nd call.
    foo.foo();
  }

  private static Object createProxyOfFoo(final Object obj) {
    Object proxyInstance = Proxy
        .newProxyInstance(Foo.class.getClassLoader(), new Class[]{Foo.class},
            new InvocationHandler() {
              @Override
              public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                System.out.println("Invoke " + method + " through proxy");
                return null;
              }
            });
    System.out.println("Created proxy class " + proxyInstance.getClass().getName()
        + " which is a runtime implementation of Foo in addition to " + obj.getClass().getName());
    return proxyInstance;
  }

  interface Foo {
    void foo();
  }

  // This is the ONLY implementation of Foo (except the one created at runtime with Proxy).
  static class Bar implements Foo {

    @Override
    public void foo() {
      System.out.println("Bar.foo");
    }
  }

}
