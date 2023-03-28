// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;
import java.util.Arrays;

public class DexSparseSwitchPayload extends DexSwitchPayload {

  public final int size;
  public final int[] keys;
  public final /* offset */ int[] targets;

  private static void specify(StructuralSpecification<DexSparseSwitchPayload, ?> spec) {
    spec.withInt(i -> i.size).withIntArray(i -> i.keys).withIntArray(i -> i.targets);
  }

  public DexSparseSwitchPayload(int high, BytecodeStream stream) {
    super(high, stream);
    size = read16BitValue(stream);
    keys = new int[size];
    for (int i = 0; i < size; i++) {
      keys[i] = readSigned32BitValue(stream);
    }

    targets = new int[size];
    for (int i = 0; i < size; i++) {
      targets[i] = readSigned32BitValue(stream);
    }
  }

  public DexSparseSwitchPayload(int[] keys, int[] targets) {
    assert targets.length > 0; // Empty switches should be eliminated.
    this.size = targets.length;
    this.keys = keys;
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
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    writeFirst(2, dest); // Pseudo-opcode = 0x0200
    write16BitValue(size, dest);
    for (int i = 0; i < size; i++) {
      write32BitValue(keys[i], dest);
    }
    for (int i = 0; i < size; i++) {
      write32BitValue(targets[i], dest);
    }
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexSparseSwitchPayload) other, DexSparseSwitchPayload::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexSparseSwitchPayload::specify);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + size;
    result = 31 * result + Arrays.hashCode(keys);
    result = 31 * result + Arrays.hashCode(targets);
    return result;
  }

  @Override
  public int getSize() {
    return 2 + (2 * keys.length) + (2 * targets.length);
  }

  @Override
  public int numberOfKeys() {
    return size;
  }

  @Override
  public int[] keys() {
    return keys;
  }

  @Override
  public int[] switchTargetOffsets() {
    return targets;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return toString(retracer, null);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer, DexInstruction payloadUser) {
    StringBuilder builder = new StringBuilder("[SparseSwitchPayload");
    if (payloadUser == null) {
      builder.append(" offsets relative to associated SparseSwitch");
    }
    builder.append("]\n");
    for (int i = 0; i < size; i++) {
      String offsetString;
      if (payloadUser != null) {
        // Don't show the decimal offset, as these are relative to the associated switch.
        offsetString = StringUtils.hexString(targets[i] + payloadUser.getOffset(), 2);
      } else {
        offsetString = targets[i] >= 0 ? ("+" + targets[i]) : Integer.toString(targets[i]);
      }
      StringUtils.appendLeftPadded(builder, keys[i] + " -> " + offsetString + "\n", 20);
    }
    return super.toString(retracer) + builder.toString();
  }

  @Override
  public String toSmaliString(DexInstruction payloadUser) {
    StringBuilder builder = new StringBuilder();
    builder.append("    ");
    builder.append(".sparse-switch");
    builder.append("\n");
    for (int i = 0; i < keys.length; i++) {
      builder.append("      ");
      builder.append(StringUtils.hexString(keys[i], 8));
      builder.append(" -> :label_");
      builder.append(payloadUser.getOffset() + targets[i]);
      builder.append("  # ");
      builder.append(keys[i]);
      builder.append("\n");
    }
    builder.append("    ");
    builder.append(".end sparse-switch");
    return builder.toString();
  }
}
