// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

interface InvalidBootstrapMethodHandleTestInterface {
  CallSite virtualMethod(MethodHandles.Lookup caller, String name, MethodType type)
      throws Exception;
}

// Test data class for InvalidBootstrapMethodHandleTest.
// Making this an inner class of the test causes changes to the VMs reflection.
public class InvalidBootstrapMethodHandleTestClass
    implements InvalidBootstrapMethodHandleTestInterface {

  // Static field to target.
  public static int staticField = 42;

  // Non-static field to target.
  public int nonStaticField = 42;

  // Virtual method to target.
  public CallSite virtualMethod(MethodHandles.Lookup caller, String name, MethodType type)
      throws Exception {
    return new ConstantCallSite(caller.findStatic(caller.lookupClass(), name, type));
  }

  // Actual valid static bootstrap method.
  public static CallSite staticMethod(MethodHandles.Lookup caller, String name, MethodType type)
      throws Exception {
    return new ConstantCallSite(caller.findStatic(caller.lookupClass(), name, type));
  }

  // Constructor to target.
  public InvalidBootstrapMethodHandleTestClass() {}

  // Called by the valid virtual invoke.
  public static void foo() {
    System.out.println("Called foo!");
  }

  public static void main(String[] args) {
    try {
      // Rewritten to invoke-dynamic for each handle type.
      foo();
    } catch (BootstrapMethodError e) {
      System.out.println(e.getCause().getMessage());
      throw e;
    }
  }
}
