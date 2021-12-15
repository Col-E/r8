// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.utils.collections.ProgramMethodSet;

public abstract class MethodProcessorWithWave extends MethodProcessor {

  protected ProgramMethodSet wave;
  protected ProgramMethodSet waveExtension = ProgramMethodSet.createConcurrent();

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return CallSiteInformation.empty();
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    return wave != null && wave.contains(method);
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    waveExtension.add(method);
  }

  protected void prepareForWaveExtensionProcessing() {
    if (waveExtension.isEmpty()) {
      wave = ProgramMethodSet.empty();
    } else {
      wave = waveExtension;
      waveExtension = ProgramMethodSet.createConcurrent();
    }
  }
}
