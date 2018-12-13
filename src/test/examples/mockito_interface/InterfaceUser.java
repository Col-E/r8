// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package mockito_interface;

public class InterfaceUser {

  private Interface itf;

  public InterfaceUser(Interface itf) {
    this.itf = itf;
  }

  void consume() {
    itf.onEnterForeground();
  }

  public static void main() {
    Implementer impl = new Implementer();
    InterfaceUser user = new InterfaceUser(impl);
    user.consume();
  }

}
