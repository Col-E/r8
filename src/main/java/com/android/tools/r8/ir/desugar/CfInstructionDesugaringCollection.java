// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.ProgramMethod;

/**
 * Abstracts a collection of low-level desugarings (i.e., mappings from class-file instructions to
 * new class-file instructions).
 *
 * <p>The combined set of low-level desugarings provide a way to desugar a method in full
 */
public abstract class CfInstructionDesugaringCollection {

  public static CfInstructionDesugaringCollection empty() {
    return new EmptyCfInstructionDesugaringCollection();
  }

  /** Desugars the instructions in the given method. */
  public abstract void desugar(ProgramMethod method, CfInstructionDesugaringEventConsumer consumer);

  /** Returns true if the given method needs desugaring. */
  public abstract boolean needsDesugaring(ProgramMethod method);
}
