// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;

public class DesugaredLibraryRetargeterL8Synthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;
  private final DexClassAndMethodSet emulatedDispatchMethods;

  public static DesugaredLibraryRetargeterL8Synthesizer create(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    assert appView.options().isDesugaredLibraryCompilation();
    if (retargetingInfo == null || retargetingInfo.getEmulatedDispatchMethods().isEmpty()) {
      assert appView.options().desugaredLibrarySpecification.getRetargetCoreLibMember().isEmpty();
      return null;
    }
    return new DesugaredLibraryRetargeterL8Synthesizer(appView, retargetingInfo);
  }

  public DesugaredLibraryRetargeterL8Synthesizer(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    emulatedDispatchMethods = retargetingInfo.getEmulatedDispatchMethods();
  }

  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    assert !emulatedDispatchMethods.isEmpty();
    for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
      syntheticHelper.ensureProgramEmulatedHolderDispatchMethod(
          emulatedDispatchMethod, eventConsumer);
    }
  }
}
