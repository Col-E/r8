// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

/**
 * Abstract view of a LIR instruction.
 *
 * <p>The view should not be considered a representation of an instruction as the underlying data
 * can change. The view callbacks allow interpreting the instruction at different levels of
 * abstraction depending on need.
 */
public interface LIRInstructionView {

  /** Convenience method to forward control to a callback. */
  void accept(LIRInstructionCallback eventCallback);

  /** The opcode of the instruction (See {@code LIROpcodes} for values). */
  int getOpcode();

  /** The remaining size of the instruction's payload. */
  int getRemainingOperandSizeInBytes();

  /** True if the instruction has any operands that have not yet been parsed. */
  boolean hasMoreOperands();

  /** Get the next operand as a constant-pool index. */
  int getNextConstantOperand();

  /** Get the next operand as an SSA value index. */
  int getNextValueOperand();
}
