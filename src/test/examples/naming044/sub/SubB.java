// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package naming044.sub;

import naming044.B;

public class SubB extends B {
  protected int f;

  public SubB(int f) {
    this.f = f;
  }

  public static int n() {
    return SubA.f;
  }
}
