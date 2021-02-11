// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;

/** Interface for desugaring a single class-file instruction. */
public interface CfInstructionDesugaring {

  /**
   * Given an instruction, returns the list of instructions that the instruction should be desugared
   * to. If no desugaring is needed, {@code null} should be returned (for efficiency).
   */
  Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext);

  /**
   * Returns true if the given instruction needs desugaring.
   *
   * <p>This should return true if-and-only-if {@link #desugarInstruction} returns non-null.
   */
  boolean needsDesugaring(CfInstruction instruction, ProgramMethod context);
}
