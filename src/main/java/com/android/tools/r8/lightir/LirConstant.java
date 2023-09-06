// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralAcceptor;

/** Interface for items that can be put in the LIR constant pool. */
public interface LirConstant {

  enum LirConstantOrder {
    // DexItem derived constants.
    STRING,
    TYPE,
    FIELD,
    METHOD,
    PROTO,
    METHOD_HANDLE,
    CALL_SITE,
    // Payload constants.
    INT_SWITCH,
    STRING_SWITCH,
    FILL_ARRAY,
    NAME_COMPUTATION,
    RECORD_FIELD_VALUES
  }

  class LirConstantStructuralAcceptor implements StructuralAcceptor<LirConstant> {
    private static final LirConstantStructuralAcceptor INSTANCE =
        new LirConstantStructuralAcceptor();

    public static LirConstantStructuralAcceptor getInstance() {
      return INSTANCE;
    }

    @Override
    public int acceptCompareTo(LirConstant item1, LirConstant item2, CompareToVisitor visitor) {
      int diff =
          visitor.visitInt(
              item1.getLirConstantOrder().ordinal(), item2.getLirConstantOrder().ordinal());
      if (diff != 0) {
        return diff;
      }
      return item1.internalLirConstantAcceptCompareTo(item2, visitor);
    }

    @Override
    public void acceptHashing(LirConstant item, HashingVisitor visitor) {
      visitor.visitInt(item.getLirConstantOrder().ordinal());
      item.internalLirConstantAcceptHashing(visitor);
    }
  }

  /** Total order on the subtypes to ensure compareTo order on the general type. */
  LirConstantOrder getLirConstantOrder();

  /**
   * Implementation of compareTo on the actual subtypes.
   *
   * <p>The implementation can assume that 'other' is of the same subtype, since it must hold that
   *
   * <pre>this.getOrder() == other.getOrder()</pre>
   */
  int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor);

  /** Implementation of hashing on the actual subtypes. */
  void internalLirConstantAcceptHashing(HashingVisitor visitor);
}
