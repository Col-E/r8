// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package memberrebinding;

import memberrebindinglib.AnIndependentInterface;

public class OtherClassExtendsOtherLibraryClass extends SuperClassOfClassExtendsOtherLibraryClass
    implements AnIndependentInterface {

  @Override
  public boolean aMethodThatReturnsTrue() {
    return true;
  }

  @Override
  public boolean aMethodThatReturnsFalse() {
    return false;
  }
}
