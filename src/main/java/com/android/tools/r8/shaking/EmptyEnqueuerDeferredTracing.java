// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import java.util.concurrent.ExecutorService;

public class EmptyEnqueuerDeferredTracing extends EnqueuerDeferredTracing {

  @Override
  public boolean deferTracingOfFieldAccess(
      DexField fieldReference,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      FieldAccessKind accessKind,
      FieldAccessMetadata metadata) {
    return false;
  }

  @Override
  public boolean enqueueWorklistActions(EnqueuerWorklist worklist) {
    return false;
  }

  @Override
  public void rewriteApplication(ExecutorService executorService) {
    // Intentionally empty.
  }

  @Override
  public void notifyReflectiveFieldAccess(ProgramField field, ProgramMethod context) {
    // Intentionally empty.
  }
}
