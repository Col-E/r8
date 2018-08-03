// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package adaptresourcefilenames;

import adaptresourcefilenames.pkg.C;
import adaptresourcefilenames.pkg.innerpkg.D;

public class TestClass {

  public static void main(String[] args) {
    new B().method();
    new C().method();
    new D().method();
  }
}
