// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagetest;

public class TestClass {

  public static void main(String[] args) {
    testAccessesToMethodOnKeptClass();
    testAccessesToMethodOnKeptClassAllowRenaming();
    testAccessesToKeptMethodOnReachableClass();
    testAccessesToKeptMethodAllowRenamingOnReachableClass();
    testAccessesToMethodOnReachableClass();
  }

  static void testAccessesToMethodOnKeptClass() {
    // 1) public method ingoing access
    AccessPublicMethodOnKeptClass.test();

    // 2) package-private method ingoing access
    AccessPackagePrivateMethodOnKeptClassDirect.test();
    AccessPackagePrivateMethodOnKeptClassIndirect.test();
  }

  static void testAccessesToMethodOnKeptClassAllowRenaming() {
    // 1) public method ingoing access
    AccessPublicMethodOnKeptClassAllowRenaming.test();

    // 2) package-private method ingoing access
    AccessPackagePrivateMethodOnKeptClassAllowRenamingDirect.test();
    AccessPackagePrivateMethodOnKeptClassAllowRenamingIndirect.test();
  }

  static void testAccessesToKeptMethodOnReachableClass() {
    // 1) public method ingoing access
    AccessPublicKeptMethodOnReachableClass.test();

    // 2) package-private method ingoing access
    AccessPackagePrivateKeptMethodOnReachableClassDirect.test();
    AccessPackagePrivateKeptMethodOnReachableClassIndirect.test();
  }

  static void testAccessesToKeptMethodAllowRenamingOnReachableClass() {
    // 1) public method ingoing access
    AccessPublicKeptMethodAllowRenamingOnReachableClass.test();

    // 2) package-private method ingoing access
    AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassDirect.test();
    AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassIndirect.test();
  }

  static void testAccessesToMethodOnReachableClass() {
    // 1) public method ingoing access
    AccessPublicMethodOnReachableClass.test();

    // 2) package-private method ingoing access
    AccessPackagePrivateMethodOnReachableClassDirect.test();
    AccessPackagePrivateMethodOnReachableClassIndirect.test();
  }
}
