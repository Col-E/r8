// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking;

public class SubClassOne implements InterfaceWithDefault, OtherInterfaceWithDefault {

  @Override
  public void foo() {
    System.out.println("Method foo from SubClassOne");
    makeSubClassTwoLive().foo();
    asOtherInterface().bar();
  }

  private OtherInterface asOtherInterface() {
    return new SubClassTwo();
  }

  @Override
  public void bar() {
    System.out.println("Method bar from SubClassOne");
  }

  private InterfaceWithDefault makeSubClassTwoLive() {
    // Once we see this method, SubClassTwo will be live. This should also make the default method
    // in the interface live, as SubClassTwo does not override it.
    return new SubClassTwo();
  }
}
