// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;

public abstract class MethodProcessor {

  public boolean isPrimaryMethodProcessor() {
    return false;
  }

  public PrimaryMethodProcessor asPrimaryMethodProcessor() {
    return null;
  }

  public boolean isPostMethodProcessor() {
    return false;
  }

  public abstract MethodProcessorEventConsumer getEventConsumer();

  public abstract boolean isProcessedConcurrently(ProgramMethod method);

  public abstract boolean shouldApplyCodeRewritings(ProgramMethod method);

  public abstract void scheduleDesugaredMethodForProcessing(ProgramMethod method);

  public abstract CallSiteInformation getCallSiteInformation();
}
