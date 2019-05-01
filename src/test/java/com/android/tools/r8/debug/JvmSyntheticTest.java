package com.android.tools.r8.debug;

public class JvmSyntheticTest {

  public static class A {

    private static void foo() {
      throw new RuntimeException("Foo");
    }
  }

  public static class Runner {

    public static void main(String[] args) {
      try {
        A.foo();
      } catch (Exception ex) {
        ex.printStackTrace(System.out);
      }
    }
  }
}
