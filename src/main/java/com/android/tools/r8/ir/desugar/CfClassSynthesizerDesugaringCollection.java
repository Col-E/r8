// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterL8Synthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.RetargetingInfo;
import com.android.tools.r8.ir.desugar.itf.ProgramEmulatedInterfaceSynthesizer;
import com.android.tools.r8.ir.desugar.records.RecordRewriter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class CfClassSynthesizerDesugaringCollection {

  public static CfClassSynthesizerDesugaringCollection create(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    Collection<CfClassSynthesizerDesugaring> synthesizers = new ArrayList<>();
    if (appView.options().isDesugaredLibraryCompilation()) {
      ProgramEmulatedInterfaceSynthesizer emulatedInterfaceSynthesizer =
          ProgramEmulatedInterfaceSynthesizer.create(appView);
      if (emulatedInterfaceSynthesizer != null) {
        synthesizers.add(emulatedInterfaceSynthesizer);
      }
      DesugaredLibraryRetargeterL8Synthesizer retargeterL8Synthesizer =
          DesugaredLibraryRetargeterL8Synthesizer.create(appView, retargetingInfo);
      if (retargeterL8Synthesizer != null) {
        synthesizers.add(retargeterL8Synthesizer);
      }
      synthesizers.add(new DesugaredLibraryWrapperSynthesizer(appView));
    }
    RecordRewriter recordRewriter = RecordRewriter.create(appView);
    if (recordRewriter != null) {
      synthesizers.add(recordRewriter);
    }
    if (synthesizers.isEmpty()) {
      return new EmptyCfClassSynthesizerCollection();
    }
    return new NonEmptyCfClassSynthesizerCollection(synthesizers);
  }

  public abstract void synthesizeClasses(
      ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer)
      throws ExecutionException;

  static class NonEmptyCfClassSynthesizerCollection extends CfClassSynthesizerDesugaringCollection {

    private final Collection<CfClassSynthesizerDesugaring> synthesizers;

    public NonEmptyCfClassSynthesizerCollection(
        Collection<CfClassSynthesizerDesugaring> synthesizers) {
      assert !synthesizers.isEmpty();
      this.synthesizers = synthesizers;
    }

    @Override
    public void synthesizeClasses(
        ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer)
        throws ExecutionException {
      ThreadUtils.processItems(
          synthesizers,
          synthesizer -> synthesizer.synthesizeClasses(eventConsumer),
          executorService);
    }
  }

  static class EmptyCfClassSynthesizerCollection extends CfClassSynthesizerDesugaringCollection {

    @Override
    public void synthesizeClasses(
        ExecutorService executorService, CfClassSynthesizerDesugaringEventConsumer eventConsumer)
        throws ExecutionException {
      // Intentionally empty.
    }
  }
}
