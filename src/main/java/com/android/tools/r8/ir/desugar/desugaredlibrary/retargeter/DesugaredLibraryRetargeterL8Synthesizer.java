// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import java.util.Map;

public class DesugaredLibraryRetargeterL8Synthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedDispatchMethods;

  public static DesugaredLibraryRetargeterL8Synthesizer create(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    assert appView.options().isDesugaredLibraryCompilation();
    if (retargetingInfo == null || retargetingInfo.getEmulatedVirtualRetarget().isEmpty()) {
      assert !appView.options().machineDesugaredLibrarySpecification.hasRetargeting();
      return null;
    }
    return new DesugaredLibraryRetargeterL8Synthesizer(appView, retargetingInfo);
  }

  public DesugaredLibraryRetargeterL8Synthesizer(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    emulatedDispatchMethods = retargetingInfo.getEmulatedVirtualRetarget();
  }

  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    assert !emulatedDispatchMethods.isEmpty();
    for (EmulatedDispatchMethodDescriptor emulatedDispatchMethod :
        emulatedDispatchMethods.values()) {
      syntheticHelper.ensureProgramEmulatedHolderDispatchMethod(
          emulatedDispatchMethod, eventConsumer);
    }
  }
}
