// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.bytecodemetadata;

import com.android.tools.r8.ir.code.Instruction;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A collection of information that pertains to the instructions in a piece of {@link
 * com.android.tools.r8.graph.CfCode} or {@link com.android.tools.r8.graph.DexCode}.
 */
public class BytecodeMetadata<I> {

  private static final BytecodeMetadata<?> EMPTY = new BytecodeMetadata<>(Collections.emptyMap());

  private final Map<I, BytecodeInstructionMetadata> backing;

  BytecodeMetadata(Map<I, BytecodeInstructionMetadata> backing) {
    assert backing.values().stream().noneMatch(Objects::isNull);
    this.backing = backing;
  }

  public static <I> Builder<I> builder(BytecodeMetadataProvider bytecodeMetadataProvider) {
    return new Builder<>(bytecodeMetadataProvider);
  }

  @SuppressWarnings("unchecked")
  public static <I> BytecodeMetadata<I> empty() {
    return (BytecodeMetadata<I>) EMPTY;
  }

  public BytecodeInstructionMetadata getMetadata(I bytecodeInstruction) {
    return backing.get(bytecodeInstruction);
  }

  public static class Builder<I> {

    private final BytecodeMetadataProvider bytecodeMetadataProvider;

    private final Map<I, BytecodeInstructionMetadata> backing = new IdentityHashMap<>();

    Builder(BytecodeMetadataProvider bytecodeMetadataProvider) {
      this.bytecodeMetadataProvider = bytecodeMetadataProvider;
    }

    public Builder<I> setMetadata(Instruction irInstruction, I bytecodeInstruction) {
      BytecodeInstructionMetadata instructionMetadata =
          bytecodeMetadataProvider.getMetadata(irInstruction);
      if (instructionMetadata != null) {
        backing.put(bytecodeInstruction, instructionMetadata);
      }
      return this;
    }

    public BytecodeMetadata<I> build() {
      return backing.isEmpty() ? empty() : new BytecodeMetadata<>(backing);
    }

    public boolean verifyNoMetadata(Instruction irInstruction) {
      assert bytecodeMetadataProvider.getMetadata(irInstruction) == null;
      return true;
    }
  }
}
