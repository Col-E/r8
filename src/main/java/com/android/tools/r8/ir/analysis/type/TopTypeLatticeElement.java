// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;

public class TopTypeLatticeElement extends TypeLatticeElement {
  private static final TopTypeLatticeElement INSTANCE = new TopTypeLatticeElement();

  private TopTypeLatticeElement() {
    super(true);
  }

  @Override
  public TypeLatticeElement asNullable() {
    return this;
  }

  static TopTypeLatticeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isTop() {
    return true;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return this;
  }

  @Override
  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    return this;
  }

  @Override
  public String toString() {
    return "TOP (everything)";
  }

  @Override
  public int hashCode() {
    return Integer.MAX_VALUE;
  }
}
