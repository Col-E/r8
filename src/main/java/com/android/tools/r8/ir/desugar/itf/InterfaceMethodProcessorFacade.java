// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

class InterfaceMethodProcessorFacade {

  private final AppView<?> appView;

  InterfaceMethodProcessorFacade(AppView<?> appView) {
    this.appView = appView;
  }

  /** Runs the interfaceProcessor, the class processor and the emulated interface processor. */
  void runInterfaceDesugaringProcessors(
      InterfaceMethodRewriter rewriter,
      IRConverter converter,
      Flavor flavour,
      ExecutorService executorService)
      throws ExecutionException {
    // During L8 compilation, emulated interfaces are processed to be renamed, to have
    // their interfaces fixed-up and to generate the emulated dispatch code.
    EmulatedInterfaceProcessor emulatedInterfaceProcessor =
        new EmulatedInterfaceProcessor(appView, rewriter);

    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    ClassProcessor classProcessor = new ClassProcessor(appView, rewriter);

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    InterfaceProcessor interfaceProcessor = new InterfaceProcessor(appView, rewriter);

    // The interface processors must be ordered so that finalization of the processing is performed
    // in that order. The emulatedInterfaceProcessor has to be last at this point to avoid renaming
    // emulated interfaces before the other processing.
    ImmutableList<InterfaceDesugaringProcessor> orderedInterfaceDesugaringProcessors =
        ImmutableList.of(classProcessor, interfaceProcessor, emulatedInterfaceProcessor);

    SortedProgramMethodSet sortedSynthesizedMethods = SortedProgramMethodSet.createConcurrent();
    processClassesConcurrently(
        orderedInterfaceDesugaringProcessors, sortedSynthesizedMethods, flavour, executorService);
    assert converter != null;
    converter.processMethodsConcurrently(sortedSynthesizedMethods, executorService);
  }

  private boolean shouldProcess(DexProgramClass clazz, Flavor flavour) {
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return false;
    }
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources);
  }

  private void processClassesConcurrently(
      List<InterfaceDesugaringProcessor> processors,
      SortedProgramMethodSet sortedSynthesizedMethods,
      Flavor flavour,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        Iterables.filter(
            appView.appInfo().classes(), (DexProgramClass clazz) -> shouldProcess(clazz, flavour)),
        clazz -> {
          for (InterfaceDesugaringProcessor processor : processors) {
            processor.process(clazz, sortedSynthesizedMethods);
          }
        },
        executorService);
    for (InterfaceDesugaringProcessor processor : processors) {
      processor.finalizeProcessing(sortedSynthesizedMethods);
    }
  }
}
