// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;

public class LongHighFrameType extends LongFrameType {

  static final LongHighFrameType SINGLETON = new LongHighFrameType();

  private LongHighFrameType() {}

  @Override
  public boolean isLong() {
    return true;
  }

  @Override
  public boolean isLongLow() {
    return false;
  }

  @Override
  public boolean isLongHigh() {
    return true;
  }

  @Override
  public boolean isWidePrimitiveLow() {
    return false;
  }

  @Override
  public boolean isWidePrimitiveHigh() {
    return true;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    throw new Unreachable();
  }

  @Override
  public String getTypeName() {
    throw new Unreachable();
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    throw new Unreachable();
  }

  @Override
  public String toString() {
    return "long-high";
  }
}
