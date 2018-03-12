package com.android.tools.r8.desugaring.interfacemethods.test0;

public interface InterfaceWithDefaults {
  default void foo() {
    System.out.println("InterfaceWithDefaults::foo()");
  }

  default void bar() {
    System.out.println("InterfaceWithDefaults::bar()");
    this.foo();
  }

  void test();
}
