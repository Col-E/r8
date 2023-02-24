// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

/**
 * Abstraction for how to encode SSA values.
 *
 * <p>At high level the unencoded SSA value index is the absolute index to the instruction defining
 * the SSA out value. This strategy provides a way to opaquely encode any references of an SSA value
 * as the relative offset from the referencing instruction.
 */
public abstract class LirSsaValueStrategy<EV> {

  private static final LirSsaValueStrategy<Integer> INSTANCE = new RelativeStrategy();

  public static LirSsaValueStrategy<Integer> get() {
    return INSTANCE;
  }

  public abstract int encodeValueIndex(EV value, int currentValueIndex);

  public abstract EV decodeValueIndex(int encodedValueIndex, int currentValueIndex);

  private static class RelativeStrategy extends LirSsaValueStrategy<Integer> {

    @Override
    public int encodeValueIndex(Integer absoluteValueIndex, int currentValueIndex) {
      assert absoluteValueIndex != null;
      return currentValueIndex - absoluteValueIndex;
    }

    @Override
    public Integer decodeValueIndex(int encodedValueIndex, int currentValueIndex) {
      return currentValueIndex - encodedValueIndex;
    }
  }
}
