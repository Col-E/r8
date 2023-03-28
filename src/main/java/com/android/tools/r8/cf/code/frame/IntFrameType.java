// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class IntFrameType extends SinglePrimitiveFrameType {

  static final IntFrameType SINGLETON = new IntFrameType();

  private IntFrameType() {}

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    return dexItemFactory.intType;
  }

  @Override
  public String getTypeName() {
    return "int";
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.INTEGER;
  }

  @Override
  public boolean hasIntVerificationType() {
    return true;
  }

  @Override
  public boolean isInt() {
    return true;
  }
}
