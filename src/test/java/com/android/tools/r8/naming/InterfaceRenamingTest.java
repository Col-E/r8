// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

public class InterfaceRenamingTest {

  public static final Class[] CLASSES = {
    InterfaceRenamingTest.class,
    InterfaceA.class,
    InterfaceB.class,
    ImplementationA1.class,
    ImplementationB1.class,
    ImplementationA2.class,
    ImplementationB2.class,
  };

  // Since the names and parameter lists of the two methods in the two interfaces are equal,
  // non-aggressive minification gives the methods the same minified name.
  // However, the return types are different, so looking up the renamed name by prototype
  // only gives a result for one of the two interfaces.
  interface InterfaceA {
    Boolean interfaceMethod();
  }

  interface InterfaceB {
    Integer interfaceMethod();
  }

  // Two implementations of each interface are required
  // to avoid the Devirtualizer hiding the buggy behavior.
  static class ImplementationA1 implements InterfaceA {
    @Override
    public Boolean interfaceMethod() {
      System.out.println("interfaceMethod1");
      return true;
    }
  }

  static class ImplementationA2 implements InterfaceA {
    @Override
    public Boolean interfaceMethod() {
      System.out.println("interfaceMethod1 dummy");
      return false;
    }
  }

  static class ImplementationB1 implements InterfaceB {
    @Override
    public Integer interfaceMethod() {
      System.out.println("interfaceMethod2");
      return 10;
    }
  }

  static class ImplementationB2 implements InterfaceB {
    @Override
    public Integer interfaceMethod() {
      System.out.println("interfaceMethod2 dummy");
      return 20;
    }
  }

  public static void main(String[] args) {
    invokeA(new ImplementationA1());
    invokeB(new ImplementationB1());
    invokeA(new ImplementationA2());
    invokeB(new ImplementationB2());
  }

  private static void invokeA(InterfaceA instance) {
    System.out.println("invokeA: " + instance.interfaceMethod());
  }

  private static void invokeB(InterfaceB instance) {
    System.out.println("invokeB: " + instance.interfaceMethod());
  }
}
