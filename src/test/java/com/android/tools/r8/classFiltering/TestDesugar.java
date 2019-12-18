package com.android.tools.r8.classFiltering;

public class TestDesugar {
  public static void main(String[] args) {
    consume(e -> System.out.print(e));
  }

  public static void consume(Consumer lambda) {
    lambda.consume("TestDesugar.consume");
  }

  interface Consumer {
    public void consume(String s);
  }
}
