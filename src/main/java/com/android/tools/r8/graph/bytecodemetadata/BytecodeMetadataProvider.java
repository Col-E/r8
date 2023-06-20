// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.bytecodemetadata;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.ir.code.Instruction;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mapping from IR instructions to the metadata that should be attached to the resulting CF or DEX
 * bytecode instruction that results from the given IR instruction.
 */
public class BytecodeMetadataProvider {

  private static final BytecodeMetadataProvider EMPTY =
      new BytecodeMetadataProvider(Collections.emptyMap());

  private final Map<Instruction, BytecodeInstructionMetadata> backing;

  BytecodeMetadataProvider(Map<Instruction, BytecodeInstructionMetadata> backing) {
    this.backing = backing;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BytecodeMetadataProvider empty() {
    return EMPTY;
  }

  public int size() {
    return backing.size();
  }

  /**
   * Returns the metadata for a given IR instruction that should be attached to the CF or DEX
   * instruction when finalizing the IR to CF or DEX.
   */
  public BytecodeInstructionMetadata getMetadata(Instruction instruction) {
    return backing.get(instruction);
  }

  public void remap(Instruction oldKey, Instruction newKey) {
    BytecodeInstructionMetadata value = backing.remove(oldKey);
    if (value != null) {
      backing.put(newKey, value);
    }
  }

  public static class Builder {

    private final Map<Instruction, BytecodeInstructionMetadata.Builder> builders =
        new IdentityHashMap<>();

    /**
     * Used to mutate the metadata that should be attached to the CF or DEX instruction that results
     * from the given IR instruction.
     */
    public Builder addMetadata(
        Instruction instruction, Consumer<BytecodeInstructionMetadata.Builder> fn) {
      assert !builders.containsKey(instruction);
      BytecodeInstructionMetadata.Builder builder =
          builders.computeIfAbsent(instruction, ignoreKey(BytecodeInstructionMetadata::builder));
      fn.accept(builder);
      return this;
    }

    public BytecodeMetadataProvider build() {
      if (builders.isEmpty()) {
        return empty();
      }
      Map<Instruction, BytecodeInstructionMetadata> backing =
          new IdentityHashMap<>(builders.size());
      builders.forEach((instruction, builder) -> backing.put(instruction, builder.build()));
      return new BytecodeMetadataProvider(backing);
    }
  }
}
