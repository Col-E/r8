// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;

/** Interface for desugaring a single class-file instruction. */
public interface CfInstructionDesugaring {

  // TODO(193004879): Merge the scan and prepare methods.
  default void scan(ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    // Default scan is to do nothing.
  }

  /**
   * Prepare step which is called on all classes scheduled for desugaring before the actual
   * instruction level desugaring is preformed. This allows the desugaring to prepare and provide
   * additional methods for program classes which will be needed for desugaring. During desugaring
   * synthetic items can be added and the instruction stream can be altered, but program methods
   * cannot be added.
   */
  default void prepare(ProgramMethod method, ProgramAdditions programAdditions) {
    // Default prepare is to do nothing.
  }

  /**
   * Given an instruction, returns the list of instructions that the instruction should be desugared
   * to. If no desugaring is needed, {@code null} should be returned (for efficiency).
   */
  Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory);

  /**
   * Returns true if the given instruction needs desugaring.
   *
   * <p>This should return true if-and-only-if {@link #desugarInstruction} returns non-null.
   */
  boolean needsDesugaring(CfInstruction instruction, ProgramMethod context);

  /**
   * Returns true if and only if needsDesugaring() answering true implies a desugaring is needed.
   * Some optimizations may have some heuristics, so that needsDesugaring() answers true in rare
   * case even if no desugaring is needed.
   */
  // TODO(b/187913003): Fixing interface desugaring should eliminate the need for this.
  default boolean hasPreciseNeedsDesugaring() {
    return true;
  }
}
