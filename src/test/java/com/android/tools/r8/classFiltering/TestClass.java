package com.android.tools.r8.classFiltering;

public class TestClass {

  public static void main(String[] args) {
    try {
      new Keep();
      System.out.print("Keep ");
    } catch (Throwable t) {
      System.out.print("No Keep ");
    }

    try {
      new Remove();
      System.out.print("Remove ");
    } catch (Throwable t) {
      System.out.print("No Remove ");
    }
  }

  public static class Keep { }

  public static class Remove { }
}
