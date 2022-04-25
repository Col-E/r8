// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import it.unimi.dsi.fastutil.bytes.ByteIterator;
import java.util.Iterator;

/**
 * Basic iterator over the light IR.
 *
 * <p>This iterator is internally a zero-allocation parser with the "elements" as a view onto the
 * current state.
 */
public class LIRIterator implements Iterator<LIRInstructionView>, LIRInstructionView {

  private final ByteIterator iterator;

  private int currentByteIndex = 0;
  private int currentOpcode = -1;
  private int currentOperandSize = 0;

  public LIRIterator(ByteIterator iterator) {
    this.iterator = iterator;
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public LIRInstructionView next() {
    currentOpcode = u1();
    if (LIROpcodes.isOneByteInstruction(currentOpcode)) {
      currentOperandSize = 0;
    } else {
      // Any instruction that is not a single byte has a two-byte header. The second byte is the
      // size of the variable width operand payload.
      currentOperandSize = u1();
      skip(currentOperandSize);
    }
    return this;
  }

  @Override
  public void accept(LIRBasicInstructionCallback eventCallback) {
    int operandsOffset = currentByteIndex - currentOperandSize;
    eventCallback.onInstruction(currentOpcode, operandsOffset, currentOperandSize);
  }

  private void skip(int i) {
    currentByteIndex += i;
    iterator.skip(i);
  }

  private int u1() {
    ++currentByteIndex;
    return ByteUtils.fromU1(iterator.nextByte());
  }
}
