// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

public class MainBase {
  public void test(MainComponent mainComponent) {
    I1 i1 = mainComponent.i1();
    I2 i2 = mainComponent.i2();
    I3 i3 = mainComponent.i3();
    System.out.println(i1 == mainComponent.i1());
    System.out.println(i2 == mainComponent.i2());
    System.out.println(i3 == mainComponent.i3());
    System.out.println(i1.getName());
    System.out.println(i2.getName());
    System.out.println(i3.getName());
  }
}
