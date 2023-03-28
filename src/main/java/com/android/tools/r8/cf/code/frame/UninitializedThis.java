// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class UninitializedThis extends UninitializedFrameType {

  static final UninitializedThis SINGLETON = new UninitializedThis();

  private UninitializedThis() {}

  @Override
  public DexType getObjectType(DexItemFactory dexItemFactory, DexType context) {
    return context;
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.UNINITIALIZED_THIS;
  }

  @Override
  public boolean isUninitializedThis() {
    return true;
  }

  @Override
  public UninitializedThis asUninitializedThis() {
    return this;
  }

  @Override
  public SingleFrameType join(
      AppView<? extends AppInfoWithClassHierarchy> appView, SingleFrameType frameType) {
    if (this == frameType) {
      return this;
    }
    return FrameType.oneWord();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "uninitialized this";
  }
}
