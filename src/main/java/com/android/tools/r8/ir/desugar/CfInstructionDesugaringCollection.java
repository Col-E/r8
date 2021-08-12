// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.RetargetingInfo;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.function.Consumer;

/**
 * Abstracts a collection of low-level desugarings (i.e., mappings from class-file instructions to
 * new class-file instructions).
 *
 * <p>The combined set of low-level desugarings provide a way to desugar a method in full
 */
public abstract class CfInstructionDesugaringCollection {

  public static CfInstructionDesugaringCollection create(AppView<?> appView) {
    if (appView.options().desugarState.isOn()) {
      return new NonEmptyCfInstructionDesugaringCollection(appView);
    }
    // TODO(b/145775365): invoke-special desugaring is mandatory, since we currently can't map
    //  invoke-special instructions that require desugaring into IR.
    if (appView.options().isGeneratingClassFiles()) {
      return NonEmptyCfInstructionDesugaringCollection.createForCfToCfNonDesugar(appView);
    }
    return NonEmptyCfInstructionDesugaringCollection.createForCfToDexNonDesugar(appView);
  }

  public static CfInstructionDesugaringCollection empty() {
    return EmptyCfInstructionDesugaringCollection.getInstance();
  }

  public abstract void prepare(ProgramMethod method, ProgramAdditions programAdditions);

  public abstract void scan(
      ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer);

  /** Desugars the instructions in the given method. */
  public abstract void desugar(
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringEventConsumer eventConsumer);

  public boolean isEmpty() {
    return false;
  }

  /** Returns true if the given method needs desugaring. */
  public abstract boolean needsDesugaring(ProgramMethod method);

  public abstract <T extends Throwable> void withD8NestBasedAccessDesugaring(
      ThrowingConsumer<D8NestBasedAccessDesugaring, T> consumer) throws T;

  public abstract InterfaceMethodProcessorFacade getInterfaceMethodPostProcessingDesugaring(
      Flavor flavor);

  public abstract RetargetingInfo getRetargetingInfo();

  public abstract void withDesugaredLibraryAPIConverter(
      Consumer<DesugaredLibraryAPIConverter> consumer);
}
