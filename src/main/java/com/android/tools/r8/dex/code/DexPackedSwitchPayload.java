// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;
import java.util.Arrays;

public class DexPackedSwitchPayload extends DexSwitchPayload {

  public final int size;
  public final int first_key;
  public final /* offset */ int[] targets;

  private static void specify(StructuralSpecification<DexPackedSwitchPayload, ?> spec) {
    spec.withInt(i -> i.size).withInt(i -> i.first_key).withIntArray(i -> i.targets);
  }

  public DexPackedSwitchPayload(int high, BytecodeStream stream) {
    super(high, stream);
    size = read16BitValue(stream);
    first_key = readSigned32BitValue(stream);
    targets = new int[size];
    for (int i = 0; i < size; i++) {
      targets[i] = readSigned32BitValue(stream);
    }
  }

  public DexPackedSwitchPayload(int first_key, int[] targets) {
    assert targets.length > 0; // Empty switches should be eliminated.
    this.size = targets.length;
    this.first_key = first_key;
    this.targets = targets;
  }

  @Override
  public boolean isPayload() {
    return true;
  }

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(1, dest); // Pseudo-opcode = 0x0100
    write16BitValue(size, dest);
    write32BitValue(first_key, dest);
    for (int i = 0; i < size; i++) {
      write32BitValue(targets[i], dest);
    }
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexPackedSwitchPayload) other, DexPackedSwitchPayload::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexPackedSwitchPayload::specify);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + size;
    result = 31 * result + first_key;
    result = 31 * result + Arrays.hashCode(targets);
    return result;
  }

  @Override
  public int getSize() {
    return 4 + (2 * targets.length);
  }

  @Override
  public int numberOfKeys() {
    return size;
  }

  @Override
  public int[] switchTargetOffsets() {
    return targets;
  }

  @Override
  public int[] keys() {
    return new int[] {first_key};
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return toString(retracer, null);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer, DexInstruction payloadUser) {
    StringBuilder builder = new StringBuilder("[PackedSwitchPayload");
    if (payloadUser == null) {
      builder.append(" offsets relative to associated PackedSwitch");
    }
    builder.append("]\n");
    for (int i = 0; i < size; i++) {
      String offsetString;
      if (payloadUser != null) {
        // Don't show the decimal offset, as these are relative to the associated switch.
        offsetString = formatOffset(targets[i] + payloadUser.getOffset());
      } else {
        offsetString = formatDecimalOffset(targets[i]);
      }
      StringUtils.appendLeftPadded(builder, (first_key + i) + " -> " + offsetString + "\n", 20);
    }
    return super.toString(retracer) + builder.toString();
  }

  @Override
  public String toSmaliString(DexInstruction payloadUser) {
    StringBuilder builder = new StringBuilder();
    builder.append("    ");
    builder.append(".packed-switch ");
    builder.append(StringUtils.hexString(first_key, 8));
    builder.append("  # ");
    builder.append(first_key);
    builder.append("\n");
    for (int target : targets) {
      builder.append("      :label_");
      builder.append(payloadUser.getOffset() + target);
      builder.append("\n");
    }
    builder.append("    ");
    builder.append(".end packed-switch");
    return builder.toString();
  }
}
