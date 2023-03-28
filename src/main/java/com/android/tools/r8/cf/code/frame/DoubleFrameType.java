// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class DoubleFrameType extends WidePrimitiveFrameType {

  static final DoubleFrameType SINGLETON = new DoubleFrameType();

  DoubleFrameType() {}

  @Override
  public DoubleFrameType getLowType() {
    return FrameType.doubleType();
  }

  @Override
  public DoubleHighFrameType getHighType() {
    return FrameType.doubleHighType();
  }

  @Override
  public boolean isDouble() {
    return true;
  }

  @Override
  public boolean isDoubleLow() {
    return true;
  }

  @Override
  public boolean isDoubleHigh() {
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
    return dexItemFactory.doubleType;
  }

  @Override
  public String getTypeName() {
    return "double";
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.DOUBLE;
  }
}
