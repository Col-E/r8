// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class BitUtils {

  public static final int ALL_BITS_SET_MASK = -1;
  public static final int ONLY_SIGN_BIT_SET_MASK = Integer.MIN_VALUE;

  public static boolean isBitSet(int value, int which) {
    return isBitInMaskSet(value, 1 << (which - 1));
  }

  public static boolean isBitInMaskSet(int value, int mask) {
    return (value & mask) != 0;
  }

  public static boolean isBitInMaskUnset(int value, int mask) {
    return !isBitInMaskSet(value, mask);
  }

  public static boolean isAligned(int alignment, int value) {
    assert (alignment & (alignment - 1)) == 0; // Check alignment is power of 2.
    return (value & (alignment - 1)) == 0;
  }
}
