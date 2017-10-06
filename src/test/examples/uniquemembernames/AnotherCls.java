// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package uniquemembernames;

public class AnotherCls {

  protected int f2;
  protected double b;

  int b() {
    return f2 * 3;
  }

  int foo() {
    return b() - (int) b;
  }

}
