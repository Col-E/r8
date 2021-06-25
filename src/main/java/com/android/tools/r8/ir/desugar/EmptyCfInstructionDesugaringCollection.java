// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassDesugaringCollection.EmptyCfClassDesugaringCollection;
import com.android.tools.r8.ir.desugar.desugaredlibrary.RetargetingInfo;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.utils.ThrowingConsumer;

public class EmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  private static final EmptyCfInstructionDesugaringCollection INSTANCE =
      new EmptyCfInstructionDesugaringCollection();

  private EmptyCfInstructionDesugaringCollection() {}

  /** Intentionally package-private, prefer {@link CfInstructionDesugaringCollection#empty()}. */
  static EmptyCfInstructionDesugaringCollection getInstance() {
    return INSTANCE;
  }

  @Override
  public void scan(ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    // Intentionally empty.
  }

  @Override
  public void desugar(
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    // Intentionally empty.
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public CfClassDesugaringCollection createClassDesugaringCollection() {
    return new EmptyCfClassDesugaringCollection();
  }

  @Override
  public boolean needsDesugaring(ProgramMethod method) {
    return false;
  }

  @Override
  public <T extends Throwable> void withD8NestBasedAccessDesugaring(
      ThrowingConsumer<D8NestBasedAccessDesugaring, T> consumer) {
    // Intentionally empty.
  }

  @Override
  public RetargetingInfo getRetargetingInfo() {
    return null;
  }
}
