// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.lightir.LIRBuilder.ValueIndexGetter;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;

public class LIRCode implements Iterable<LIRInstructionView> {

  private final IRMetadata metadata;

  /** Constant pool of items. */
  private final DexItem[] constants;

  /** Full number of arguments (including receiver for non-static methods). */
  private final int argumentCount;

  /** Byte encoding of the instructions (including phis). */
  private final byte[] instructions;

  /** Cached value for the number of logical instructions (including phis). */
  private final int instructionCount;

  public static <V> LIRBuilder<V> builder(ValueIndexGetter<V> valueIndexGetter) {
    return new LIRBuilder<V>(valueIndexGetter);
  }

  // Should be constructed using LIRBuilder.
  LIRCode(
      IRMetadata metadata,
      DexItem[] constants,
      int argumentCount,
      byte[] instructions,
      int instructionCount) {
    this.metadata = metadata;
    this.constants = constants;
    this.argumentCount = argumentCount;
    this.instructions = instructions;
    this.instructionCount = instructionCount;
  }

  public int getArgumentCount() {
    return argumentCount;
  }

  public byte[] getInstructionBytes() {
    return instructions;
  }

  public int getInstructionCount() {
    return instructionCount;
  }

  public IRMetadata getMetadata() {
    return metadata;
  }

  public DexItem getConstantItem(int index) {
    return constants[index];
  }

  @Override
  public LIRIterator iterator() {
    return new LIRIterator(new ByteArrayIterator(instructions));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("LIRCode{");
    builder.append("constants:");
    StringUtils.append(builder, Arrays.asList(constants));
    builder
        .append(", arguments:")
        .append(argumentCount)
        .append(", instructions(size:")
        .append(instructions.length)
        .append("):{");
    int index = 0;
    for (LIRInstructionView view : this) {
      builder
          .append(LIROpcodes.toString(view.getOpcode()))
          .append("(size:")
          .append(1 + view.getRemainingOperandSizeInBytes())
          .append(")");
      if (index++ < instructionCount) {
        builder.append(",");
      }
    }
    builder.append("}}");
    return builder.toString();
  }
}
