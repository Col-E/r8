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
public class LirIterator implements Iterator<LirInstructionView>, LirInstructionView {

  private final ByteIterator iterator;

  // State of the byte offsets into the iterator.
  private int currentByteIndex = 0;
  private int endOfCurrentInstruction = 0;

  // State of the instruction interpretation.
  private int currentInstructionIndex = -1;
  private int currentOpcode = -1;

  public LirIterator(ByteIterator iterator) {
    this.iterator = iterator;
  }

  private void skipRemainingOperands() {
    if (hasMoreOperands()) {
      skip(getRemainingOperandSizeInBytes());
    }
  }

  @Override
  public boolean hasNext() {
    skipRemainingOperands();
    return iterator.hasNext();
  }

  @Override
  public LirInstructionView next() {
    skipRemainingOperands();
    ++currentInstructionIndex;
    currentOpcode = u1();
    if (LirOpcodes.isOneByteInstruction(currentOpcode)) {
      endOfCurrentInstruction = currentByteIndex;
    } else {
      // Any instruction that is not a single byte has a two-byte header. The second byte is the
      // size of the variable width operand payload.
      int operandSize = u1();
      if (operandSize == 0) {
        // Zero is used to indicate the operand size is larger than a u1 encoded value.
        operandSize = u4();
      }
      endOfCurrentInstruction = currentByteIndex + operandSize;
    }
    return this;
  }

  @Override
  public void accept(LirInstructionCallback eventCallback) {
    eventCallback.onInstructionView(this);
  }

  @Override
  public int getInstructionIndex() {
    return currentInstructionIndex;
  }

  @Override
  public int getOpcode() {
    return currentOpcode;
  }

  @Override
  public int getRemainingOperandSizeInBytes() {
    return endOfCurrentInstruction - currentByteIndex;
  }

  @Override
  public boolean hasMoreOperands() {
    return currentByteIndex < endOfCurrentInstruction;
  }

  @Override
  public int getNextIntegerOperand() {
    assert hasMoreOperands();
    return u4();
  }

  @Override
  public long getNextLongOperand() {
    assert hasMoreOperands();
    return u8();
  }

  @Override
  public int getNextConstantOperand() {
    return getNextIntegerOperand();
  }

  @Override
  public int getNextValueOperand() {
    return getNextIntegerOperand();
  }

  @Override
  public int getNextBlockOperand() {
    return getNextIntegerOperand();
  }

  @Override
  public int getNextU1() {
    return u1();
  }

  private void skip(int i) {
    currentByteIndex += i;
    iterator.skip(i);
  }

  private int u1() {
    ++currentByteIndex;
    return ByteUtils.fromU1(iterator.nextByte());
  }

  private int u4() {
    currentByteIndex += 4;
    return ByteUtils.readEncodedInt(iterator);
  }

  private long u8() {
    currentByteIndex += 8;
    return ByteUtils.readEncodedLong(iterator);
  }
}
