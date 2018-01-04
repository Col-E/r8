// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package staticinlining;

import inlining.AlwaysInline;

public class Sub1 extends SuperClass {

  @AlwaysInline
  public static void inlineMe() {
    Main.printMessage("Sub1");
  }

}
