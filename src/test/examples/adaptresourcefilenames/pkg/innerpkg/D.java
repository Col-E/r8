// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package adaptresourcefilenames.pkg.innerpkg;

import adaptresourcefilenames.NoInliningOfDefaultInitializer;

@NoInliningOfDefaultInitializer
public class D {

  public void method() {
    System.out.println("In D.method()");
  }
}
