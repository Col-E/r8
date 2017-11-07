// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package shaking;

public class Shaking {

  public static void main(String... args) {
    SubClassOne anInstance = new SubClassOne();
    invokeFooOnInterface(anInstance);
  }

  private static void invokeFooOnInterface(InterfaceWithDefault anInstance) {
    anInstance.foo();
  }
}
