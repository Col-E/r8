// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  private static final EmptyCfInstructionDesugaringCollection INSTANCE =
      new EmptyCfInstructionDesugaringCollection();

  private EmptyCfInstructionDesugaringCollection() {}

  /** Intentionally package-private, prefer {@link CfInstructionDesugaringCollection#empty()}. */
  static EmptyCfInstructionDesugaringCollection getInstance() {
    return INSTANCE;
  }

  @Override
  public void prepare(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramAdditions additionalProgramMethods) {
    // Intentionally empty.
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
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfDesugaringInfo desugaringInfo,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    // Nothing to desugar.
    return null;
  }

  @Override
  public boolean isEmpty() {
    return true;
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
  public InterfaceMethodProcessorFacade getInterfaceMethodPostProcessingDesugaringD8(
      Flavor flavor, InterfaceProcessor interfaceProcessor) {
    return null;
  }

  @Override
  public InterfaceMethodProcessorFacade getInterfaceMethodPostProcessingDesugaringR8(
      Flavor flavor, Predicate<ProgramMethod> isLiveMethod, InterfaceProcessor processor) {
    return null;
  }

  @Override
  public void withDesugaredLibraryAPIConverter(Consumer<DesugaredLibraryAPIConverter> consumer) {
    // Intentionally empty.
  }
}
