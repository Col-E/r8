// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterL8Synthesizer;
import com.android.tools.r8.ir.desugar.itf.ProgramEmulatedInterfaceSynthesizer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaring;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaring;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public abstract class CfClassSynthesizerDesugaringCollection {

  public static CfClassSynthesizerDesugaringCollection create(AppView<?> appView) {
    Collection<CfClassSynthesizerDesugaring> synthesizers = new ArrayList<>();
    if (appView.options().isDesugaredLibraryCompilation()) {
      ProgramEmulatedInterfaceSynthesizer emulatedInterfaceSynthesizer =
          ProgramEmulatedInterfaceSynthesizer.create(appView);
      if (emulatedInterfaceSynthesizer != null) {
        synthesizers.add(emulatedInterfaceSynthesizer);
      }
      DesugaredLibraryRetargeterL8Synthesizer retargeterL8Synthesizer =
          DesugaredLibraryRetargeterL8Synthesizer.create(appView);
      if (retargeterL8Synthesizer != null) {
        synthesizers.add(retargeterL8Synthesizer);
      }
      synthesizers.add(new DesugaredLibraryWrapperSynthesizer(appView));
    }
    RecordDesugaring recordRewriter = RecordDesugaring.create(appView);
    if (recordRewriter != null) {
      synthesizers.add(recordRewriter);
    }
    VarHandleDesugaring varHandleDesugaring = VarHandleDesugaring.create(appView);
    if (varHandleDesugaring != null) {
      synthesizers.add(varHandleDesugaring);
    }
    if (synthesizers.isEmpty()) {
      return new EmptyCfClassSynthesizerCollection();
    }
    return new NonEmptyCfClassSynthesizerCollection(appView, synthesizers);
  }

  public abstract void synthesizeClasses(
      ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer)
      throws ExecutionException;

  static class NonEmptyCfClassSynthesizerCollection extends CfClassSynthesizerDesugaringCollection {

    private final AppView<?> appView;
    private final Collection<CfClassSynthesizerDesugaring> synthesizers;

    public NonEmptyCfClassSynthesizerCollection(
        AppView<?> appView, Collection<CfClassSynthesizerDesugaring> synthesizers) {
      assert !synthesizers.isEmpty();
      this.appView = appView;
      this.synthesizers = synthesizers;
    }

    @Override
    public void synthesizeClasses(
        ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer)
        throws ExecutionException {
      assert synthesizers.stream()
              .map(CfClassSynthesizerDesugaring::uniqueIdentifier)
              .collect(Collectors.toSet())
              .size()
          == synthesizers.size();
      ProcessorContext processorContext = appView.createProcessorContext();
      ThreadUtils.processItems(
          synthesizers,
          synthesizer -> {
            ClassSynthesisDesugaringContext classSynthesisDesugaringContext =
                processorContext.createClassSynthesisDesugaringContext(synthesizer);
            synthesizer.synthesizeClasses(classSynthesisDesugaringContext, eventConsumer);
          },
          appView.options().getThreadingModule(),
          executorService);
    }
  }

  static class EmptyCfClassSynthesizerCollection extends CfClassSynthesizerDesugaringCollection {

    @Override
    public void synthesizeClasses(
        ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
      // Intentionally empty.
    }
  }
}
