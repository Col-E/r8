// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DexItem;

public class LIRCode implements Iterable<LIRInstructionView> {

  private final DexItem[] constants;
  private final byte[] instructions;

  public static LIRBuilder builder() {
    return new LIRBuilder();
  }

  // Should be constructed using LIRBuilder.
  LIRCode(DexItem[] constants, byte[] instructions) {
    this.constants = constants;
    this.instructions = instructions;
  }

  @Override
  public LIRIterator iterator() {
    return new LIRIterator(new ByteArrayIterator(instructions));
  }
}
