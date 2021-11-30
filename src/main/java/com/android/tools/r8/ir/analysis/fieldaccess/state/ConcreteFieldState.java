// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

/** A shared base class for non-trivial field information (neither bottom nor top). */
public abstract class ConcreteFieldState extends FieldState {

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteFieldState asConcrete() {
    return this;
  }
}
