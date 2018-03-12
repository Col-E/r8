package com.android.tools.r8.desugaring.interfacemethods.test0;

public class TestMain implements InterfaceWithDefaults {
  @Override
  public void test() {
    System.out.println("TestMain::test()");
    this.foo();
    this.bar();
  }

  @Override
  public void foo() {
    System.out.println("TestMain::foo()");
  }

  public static void main(String[] args) {
    new TestMain().test();
  }
}
