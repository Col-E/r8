// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;

public abstract class MethodProcessor {

  protected SortedProgramMethodSet wave;
  protected SortedProgramMethodSet waveExtension = SortedProgramMethodSet.createConcurrent();

  public abstract boolean shouldApplyCodeRewritings(ProgramMethod method);

  public boolean isPrimaryMethodProcessor() {
    return false;
  }

  public CallSiteInformation getCallSiteInformation() {
    return CallSiteInformation.empty();
  }

  public boolean isProcessedConcurrently(ProgramMethod method) {
    return wave != null && wave.contains(method);
  }

  public void scheduleMethodForProcessingAfterCurrentWave(ProgramMethod method) {
    waveExtension.add(method);
  }

  protected void prepareForWaveExtensionProcessing() {
    if (waveExtension.isEmpty()) {
      wave = SortedProgramMethodSet.empty();
    } else {
      wave = waveExtension;
      waveExtension = SortedProgramMethodSet.createConcurrent();
    }
  }
}
