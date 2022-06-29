// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;

public abstract class CfArrayLoadOrStore extends CfInstruction {

  private final MemberType type;

  CfArrayLoadOrStore(MemberType type) {
    assert type.isPrecise();
    this.type = type;
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 1;
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  DexType getExpectedArrayType(DexItemFactory dexItemFactory) {
    switch (type) {
      case OBJECT:
        return dexItemFactory.objectArrayType;
      case BOOLEAN_OR_BYTE:
        return dexItemFactory.intArrayType;
      case CHAR:
        return dexItemFactory.charArrayType;
      case SHORT:
        return dexItemFactory.shortArrayType;
      case INT:
        return dexItemFactory.intArrayType;
      case FLOAT:
        return dexItemFactory.floatArrayType;
      case LONG:
        return dexItemFactory.longArrayType;
      case DOUBLE:
        return dexItemFactory.doubleArrayType;
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public MemberType getType() {
    return type;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to add.
  }
}
