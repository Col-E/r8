// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.ir.desugar.CfL8ClassSynthesizer;
import com.android.tools.r8.ir.desugar.CfL8ClassSynthesizerEventConsumer;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class DesugaredLibraryRetargeterL8Synthesizer implements CfL8ClassSynthesizer {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;
  private final DexClassAndMethodSet emulatedDispatchMethods;

  public static DesugaredLibraryRetargeterL8Synthesizer create(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    assert appView.options().isDesugaredLibraryCompilation();
    if (retargetingInfo == null || retargetingInfo.getEmulatedDispatchMethods().isEmpty()) {
      assert appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty();
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
  public List<Future<?>> synthesizeClasses(
      ExecutorService executorService, CfL8ClassSynthesizerEventConsumer eventConsumer) {
    assert !emulatedDispatchMethods.isEmpty();
    List<Future<?>> futures = new ArrayList<>();
    for (DexClassAndMethod emulatedDispatchMethod : emulatedDispatchMethods) {
      futures.add(
          executorService.submit(
              () -> {
                syntheticHelper.ensureEmulatedHolderDispatchMethod(
                    emulatedDispatchMethod, eventConsumer);
                return null;
              }));
    }
    return futures;
  }
}
