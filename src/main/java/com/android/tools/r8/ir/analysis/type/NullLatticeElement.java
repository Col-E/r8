// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;

public class NullLatticeElement extends TypeLatticeElement {
  private static final NullLatticeElement INSTANCE = new NullLatticeElement();

  private NullLatticeElement() {
    super(true);
  }

  @Override
  public boolean mustBeNull() {
    return true;
  }

  @Override
  TypeLatticeElement asNullable() {
    return this;
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return BottomTypeLatticeElement.getInstance();
  }

  public static NullLatticeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return this;
  }

  @Override
  public String toString() {
    return "NULL";
  }
}
