// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.BitSet;

public class BitSetUtils {

  @SuppressWarnings("unchecked")
  public static BitSet or(BitSet bitSet, BitSet other) {
    BitSet newBitSet = (BitSet) bitSet.clone();
    newBitSet.or(other);
    return newBitSet;
  }

  public static boolean verifyLessThanOrEqualTo(BitSet bitSet, BitSet other) {
    assert other.equals(or(bitSet, other));
    return true;
  }
}
