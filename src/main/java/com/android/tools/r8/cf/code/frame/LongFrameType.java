// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class LongFrameType extends WidePrimitiveFrameType {

  static final LongFrameType SINGLETON = new LongFrameType();

  LongFrameType() {}

  @Override
  public LongFrameType getLowType() {
    return FrameType.longType();
  }

  @Override
  public LongHighFrameType getHighType() {
    return FrameType.longHighType();
  }

  @Override
  public boolean isLong() {
    return true;
  }

  @Override
  public boolean isLongLow() {
    return true;
  }

  @Override
  public boolean isLongHigh() {
    return false;
  }

  @Override
  public boolean isWidePrimitiveLow() {
    return true;
  }

  @Override
  public boolean isWidePrimitiveHigh() {
    return false;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    return dexItemFactory.longType;
  }

  @Override
  public String getTypeName() {
    return "long";
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.LONG;
  }
}
