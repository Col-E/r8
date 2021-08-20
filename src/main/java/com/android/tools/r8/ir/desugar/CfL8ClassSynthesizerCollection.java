// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterL8Synthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.RetargetingInfo;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizer;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CfL8ClassSynthesizerCollection {

  private Collection<CfL8ClassSynthesizer> synthesizers = new ArrayList<>();

  public CfL8ClassSynthesizerCollection(AppView<?> appView, RetargetingInfo retargetingInfo) {
    assert appView.options().isDesugaredLibraryCompilation();
    EmulatedInterfaceSynthesizer emulatedInterfaceSynthesizer =
        EmulatedInterfaceSynthesizer.create(appView);
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

  public void synthesizeClasses(
      ExecutorService executorService, CfL8ClassSynthesizerEventConsumer eventConsumer)
      throws ExecutionException {
    ThreadUtils.processItems(
        synthesizers, synthesizer -> synthesizer.synthesizeClasses(eventConsumer), executorService);
  }
}
