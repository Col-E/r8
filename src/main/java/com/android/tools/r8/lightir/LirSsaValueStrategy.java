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
public abstract class LirSsaValueStrategy {

  private static final LirSsaValueStrategy INSTANCE = new RelativeStrategy();

  public static LirSsaValueStrategy get() {
    return INSTANCE;
  }

  public abstract int encodeValueIndex(int absoluteValueIndex, int currentValueIndex);

  public abstract int decodeValueIndex(int encodedValueIndex, int currentValueIndex);

  private static class AbsoluteStrategy extends LirSsaValueStrategy {

    @Override
    public int encodeValueIndex(int absoluteValueIndex, int currentValueIndex) {
      return absoluteValueIndex;
    }

    @Override
    public int decodeValueIndex(int encodedValueIndex, int currentValueIndex) {
      return encodedValueIndex;
    }
  }

  private static class RelativeStrategy extends LirSsaValueStrategy {

    @Override
    public int encodeValueIndex(int absoluteValueIndex, int currentValueIndex) {
      return currentValueIndex - absoluteValueIndex;
    }

    @Override
    public int decodeValueIndex(int encodedValueIndex, int currentValueIndex) {
      return currentValueIndex - encodedValueIndex;
    }
  }
}
