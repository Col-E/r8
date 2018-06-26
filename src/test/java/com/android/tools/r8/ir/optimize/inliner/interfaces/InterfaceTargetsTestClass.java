// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.interfaces;

public class InterfaceTargetsTestClass {
  public static void main(String[] args) {
    System.out.println(testInterfaceNoImpl(null));
    System.out.println(testInterfaceA(null));
    System.out.println(testInterfaceB(null));
    System.out.println(testInterfaceC(null));
    System.out.println(testInterfaceD(null));
  }

  public interface IfaceNoImpl {
    void foo();
  }

  public static String testInterfaceNoImpl(IfaceNoImpl iface) {
    if (iface != null) {
      iface.foo();
    }
    return "testInterfaceNoImpl::OK";
  }

  public interface IfaceA {
    void foo();
  }

  public static class BaseA {
    public void foo() {
    }
  }

  public static class DerivedA extends BaseA implements IfaceA {
  }

  public static String testInterfaceA(IfaceA iface) {
    if (iface != null) {
      iface.foo();
    }
    return "testInterfaceA::OK";
  }

  public interface IfaceB {
    void foo();
  }

  public abstract static class BaseB implements IfaceB {
  }

  public static class DerivedB extends BaseB {
    public void foo() {
    }
  }

  public static String testInterfaceB(IfaceB iface) {
    if (iface != null) {
      iface.foo();
    }
    return "testInterfaceB::OK";
  }

  public interface IfaceC {
    void foo();
  }

  public interface IfaceC2 extends IfaceC {
    default void foo() {
    }
  }

  public abstract static class BaseC implements IfaceC {
  }

  public static class DerivedC extends BaseC implements IfaceC2 {
  }

  public static String testInterfaceC(IfaceC iface) {
    if (iface != null) {
      iface.foo();
    }
    return "testInterfaceC::OK";
  }

  public interface IfaceD {
    void foo();
  }

  public static class BaseD implements IfaceD {
    public void foo() {
    }
  }

  public static String testInterfaceD(IfaceD iface) {
    if (iface != null) {
      iface.foo();
    }
    return "testInterfaceD::OK";
  }
}
