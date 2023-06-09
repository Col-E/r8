// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;
import java.util.Arrays;

public class DexFillArrayDataPayload extends DexNop {

  public final int element_width;
  public final long size;
  public final short[] data;

  private static void specify(StructuralSpecification<DexFillArrayDataPayload, ?> spec) {
    spec.withInt(i -> i.element_width).withLong(i -> i.size).withShortArray(i -> i.data);
  }

  DexFillArrayDataPayload(int high, BytecodeStream stream) {
    super(high, stream);
    element_width = read16BitValue(stream);
    size = read32BitValue(stream);
    assert size * element_width < Integer.MAX_VALUE;
    // Read the data as shorts (which is faster than reading bytes) as they are always 2-byte
    // aligned and the size is 2-byte aligned as well.
    int numberOfShorts = (int) (size * element_width + 1) / 2;
    data = new short[numberOfShorts];
    for (int i = 0; i < data.length; i++) {
      data[i] = readSigned16BitValue(stream);
    }
  }

  public DexFillArrayDataPayload(int element_width, long size, short[] data) {
    this.element_width = element_width;
    this.size = size;
    this.data = data;
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
    writeFirst(3, dest); // Pseudo-opcode = 0x0300
    write16BitValue(element_width, dest);
    write32BitValue(size, dest);
    for (short datum : data) {
      write16BitValue(datum, dest);
    }
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFillArrayDataPayload) other, DexFillArrayDataPayload::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFillArrayDataPayload::specify);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + element_width;
    result = 31 * result + (int) (size ^ (size >>> 32));
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public int getSize() {
    return 4 + data.length;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return super.toString(retracer)
        + "[FillArrayPayload], "
        + "width: "
        + element_width
        + ", size:  "
        + size;
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    builder.append("    ");
    builder.append(".array-data ");
    builder.append(StringUtils.hexString(element_width, 1));
    builder.append("  # ");
    builder.append(element_width);
    builder.append("\n");
    if (element_width == 1) {
      // For element width 1 split the 16-bit data units into bytes.
      for (int i = 0; i < data.length; i++) {
        for (int j = 0; j < 2; j++) {
          int value = (data[i] >> (j * 8)) & 0xff;
          if (i * 2 + j < size) {
            builder.append("      ");
            builder.append(StringUtils.hexString(value, 2));
            builder.append("  # ");
            builder.append(value);
            builder.append("\n");
          }
        }
      }
    } else {
      // For element width > 1 combine the 16-bit data units into 16-bit/32-bit or 64-bit values.
      assert element_width == 2 || element_width == 4 || element_width == 8;
      long value = 0;
      for (int i = 0; i < data.length; i++) {
        value = (Short.toUnsignedLong(data[i]) << (16 * (i % (element_width / 2)))) | value;
        if ((((i + 1) * 2) % element_width) == 0) {
          builder.append("      ");
          builder.append(StringUtils.hexString(value, element_width * 2));
          builder.append("  # ");
          builder.append(value);
          builder.append("\n");
          value = 0;
        }
      }
    }
    builder.append("    ");
    builder.append(".end array-data");
    return builder.toString();
  }

  @Override
  public void buildIR(IRBuilder builder) {
    // FilledArrayData payloads are not represented in the IR.
  }
}
