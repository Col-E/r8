// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

public interface LIRBasicInstructionCallback {

  /**
   * Most basic callback for interpreting LIR.
   *
   * @param opcode The opcode of the instruction (See {@code LIROpcodes} for values).
   * @param operandsOffsetInBytes The offset into the byte stream at which the instruction's payload
   *     starts.
   * @param operandsSizeInBytes The total size of the instruction's payload (excluding the opcode
   *     itself an any payload size encoding).
   */
  void onInstruction(int opcode, int operandsOffsetInBytes, int operandsSizeInBytes);
}
