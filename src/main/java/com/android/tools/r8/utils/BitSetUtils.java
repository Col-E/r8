// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.BitSet;

public class BitSetUtils {

  public static BitSet createFilled(boolean value, int length) {
    BitSet bitSet = new BitSet(length);
    for (int i = 0; i < length; i++) {
      bitSet.set(i, value);
    }
    return bitSet;
  }

  @SuppressWarnings("unchecked")
  public static BitSet or(BitSet bitSet, BitSet other) {
    BitSet newBitSet = (BitSet) bitSet.clone();
    newBitSet.or(other);
    return newBitSet;
  }

  // A null bit set is interpreted as the empty bit set.
  public static BitSet intersectNullableBitSets(BitSet bitSet, BitSet other) {
    if (bitSet == null || other == null) {
      return null;
    }
    BitSet result = (BitSet) bitSet.clone();
    result.and(other);
    return result.isEmpty() ? null : result;
  }

  public static boolean verifyLessThanOrEqualTo(BitSet bitSet, BitSet other) {
    assert other.equals(or(bitSet, other));
    return true;
  }
}
