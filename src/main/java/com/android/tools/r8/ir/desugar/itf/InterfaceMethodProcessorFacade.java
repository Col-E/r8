// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class InterfaceMethodProcessorFacade implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final Flavor flavour;
  private final InterfaceProcessor interfaceProcessor;
  private final ClassProcessor classProcessor;

  InterfaceMethodProcessorFacade(
      AppView<?> appView,
      Flavor flavour,
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceProcessor interfaceProcessor,
      InterfaceMethodDesugaringMode desugaringMode) {
    this.appView = appView;
    this.flavour = flavour;
    assert interfaceProcessor != null;
    this.interfaceProcessor = interfaceProcessor;
    this.classProcessor = new ClassProcessor(appView, isLiveMethod, desugaringMode);
  }

  private boolean shouldProcess(DexProgramClass clazz, Flavor flavour) {
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return false;
    }
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources);
  }

  private void processClassesConcurrently(
      Collection<DexProgramClass> programClasses,
      InterfaceProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        ListUtils.filter(programClasses, clazz -> shouldProcess(clazz, flavour)),
        clazz -> classProcessor.process(clazz, eventConsumer),
        appView.options().getThreadingModule(),
        executorService);
    classProcessor.finalizeProcessing(eventConsumer, executorService);
    interfaceProcessor.finalizeProcessing();
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    processClassesConcurrently(programClasses, eventConsumer, executorService);
  }
}
