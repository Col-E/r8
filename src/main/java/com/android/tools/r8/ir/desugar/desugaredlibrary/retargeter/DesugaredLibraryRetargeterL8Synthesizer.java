// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import java.util.Map;

public class DesugaredLibraryRetargeterL8Synthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  public static DesugaredLibraryRetargeterL8Synthesizer create(AppView<?> appView) {
    assert appView.options().isDesugaredLibraryCompilation();
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .getEmulatedVirtualRetarget()
        .isEmpty()) {
      return null;
    }
    return new DesugaredLibraryRetargeterL8Synthesizer(appView);
  }

  public DesugaredLibraryRetargeterL8Synthesizer(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
  }

  @Override
  public String uniqueIdentifier() {
    return "$retargeter$";
  }

  @Override
  public void synthesizeClasses(
      ClassSynthesisDesugaringContext processingContext,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget =
        appView.options().machineDesugaredLibrarySpecification.getEmulatedVirtualRetarget();
    for (EmulatedDispatchMethodDescriptor emulatedDispatchMethod :
        emulatedVirtualRetarget.values()) {
      syntheticHelper.ensureProgramEmulatedHolderDispatchMethod(
          emulatedDispatchMethod, eventConsumer);
    }
  }
}
