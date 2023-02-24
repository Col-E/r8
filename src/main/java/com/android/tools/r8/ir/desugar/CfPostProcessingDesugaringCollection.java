// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPICallbackSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.DesugaredLibraryDisableDesugarerPostProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterPostProcessor;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.records.RecordDesugaring;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class CfPostProcessingDesugaringCollection {

  public static CfPostProcessingDesugaringCollection create(
      AppView<?> appView,
      InterfaceMethodProcessorFacade interfaceMethodProcessorFacade,
      Predicate<ProgramMethod> isLiveMethod) {
    if (appView.options().desugarState.isOn()) {
      return NonEmptyCfPostProcessingDesugaringCollection.create(
          appView, interfaceMethodProcessorFacade, isLiveMethod);
    }
    return empty();
  }

  static CfPostProcessingDesugaringCollection empty() {
    return EmptyCfPostProcessingDesugaringCollection.getInstance();
  }

  public abstract void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException;

  public static class NonEmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private final List<CfPostProcessingDesugaring> desugarings;

    public NonEmptyCfPostProcessingDesugaringCollection(
        List<CfPostProcessingDesugaring> desugarings) {
      this.desugarings = desugarings;
    }

    public static CfPostProcessingDesugaringCollection create(
        AppView<?> appView,
        InterfaceMethodProcessorFacade interfaceMethodProcessorFacade,
        Predicate<ProgramMethod> isLiveMethod) {
      ArrayList<CfPostProcessingDesugaring> desugarings = new ArrayList<>();
      if (appView.options().machineDesugaredLibrarySpecification.hasRetargeting()
          && !appView.options().isDesugaredLibraryCompilation()) {
        desugarings.add(new DesugaredLibraryRetargeterPostProcessor(appView));
      }
      if (interfaceMethodProcessorFacade != null) {
        desugarings.add(interfaceMethodProcessorFacade);
      }
      DesugaredLibraryAPICallbackSynthesizer apiCallbackSynthesizor =
          appView.typeRewriter.isRewriting()
              ? new DesugaredLibraryAPICallbackSynthesizer(appView, isLiveMethod)
              : null;
      // At this point the desugaredLibraryAPIConverter is required to be last to generate
      // call-backs on the forwarding methods.
      if (apiCallbackSynthesizor != null) {
        desugarings.add(apiCallbackSynthesizor);
      }
      RecordDesugaring recordRewriter = RecordDesugaring.create(appView);
      if (recordRewriter != null) {
        desugarings.add(recordRewriter);
      }
      DesugaredLibraryDisableDesugarerPostProcessor disableDesugarer =
          DesugaredLibraryDisableDesugarerPostProcessor.create(appView);
      if (disableDesugarer != null) {
        desugarings.add(disableDesugarer);
      }
      if (desugarings.isEmpty()) {
        return empty();
      }
      return new NonEmptyCfPostProcessingDesugaringCollection(desugarings);
    }

    @Override
    public void postProcessingDesugaring(
        Collection<DexProgramClass> programClasses,
        CfPostProcessingDesugaringEventConsumer eventConsumer,
        ExecutorService executorService)
        throws ExecutionException {
      for (CfPostProcessingDesugaring desugaring : desugarings) {
        desugaring.postProcessingDesugaring(programClasses, eventConsumer, executorService);
      }
    }
  }

  public static class EmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private static final EmptyCfPostProcessingDesugaringCollection INSTANCE =
        new EmptyCfPostProcessingDesugaringCollection();

    private EmptyCfPostProcessingDesugaringCollection() {}

    private static EmptyCfPostProcessingDesugaringCollection getInstance() {
      return INSTANCE;
    }

    @Override
    public void postProcessingDesugaring(
        Collection<DexProgramClass> programClasses,
        CfPostProcessingDesugaringEventConsumer eventConsumer,
        ExecutorService executorService) {
      // Intentionally empty.
    }
  }
}
