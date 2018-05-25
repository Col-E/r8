// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package abstractmethodremoval.b;

import abstractmethodremoval.a.Public;

public class Impl1 extends Public {
  @Override
  public void foo(int i) {
    System.out.println("Impl1.foo(" + i + ")");
  }
}
