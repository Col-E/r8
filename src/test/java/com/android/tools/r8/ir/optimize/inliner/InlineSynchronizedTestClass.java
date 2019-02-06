// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

class InlineSynchronizedTestClass {
  private synchronized void normalInlinedSynchronized() {
    System.out.println("InlineSynchronizedTestClass::normalInlinedSynchronized");
  }

  public synchronized void classInlinedSynchronized() {
    System.out.println("InlineSynchronizedTestClass::classInlinedSynchronized");
  }

  private void normalInlinedControl() {
    System.out.println("InlineSynchronizedTestClass::normalInlinedControl");
  }

  public void classInlinedControl() {
    System.out.println("InlineSynchronizedTestClass::classInlinedControl");
  }

  public static void main(String[] args) {
    // Test normal inlining.
    InlineSynchronizedTestClass testClass = new InlineSynchronizedTestClass();
    testClass.normalInlinedSynchronized();
    testClass.normalInlinedControl();

    // Test class-inlining.
    new InlineSynchronizedTestClass().classInlinedSynchronized();
    new InlineSynchronizedTestClass().classInlinedControl();
  }
}
