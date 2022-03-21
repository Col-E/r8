// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnqueuerDeferredTracing {

  /**
   * Returns true if the {@link Enqueuer} should not trace the given field reference.
   *
   * <p>If for some reason the field reference should be traced after all, a worklist item can be
   * enqueued upon reaching a (preliminary) fixpoint in {@link
   * #enqueueWorklistActions(EnqueuerWorklist)}, which will cause tracing to continue.
   */
  public boolean deferTracingOfFieldAccess(
      DexField fieldReference,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      FieldAccessKind kind,
      FieldAccessMetadata metadata) {
    return false;
  }

  /**
   * Called when the {@link EnqueuerWorklist} is empty, to allow additional tracing before ending
   * tree shaking.
   */
  public boolean enqueueWorklistActions(EnqueuerWorklist worklist) {
    return false;
  }

  /**
   * Called when tree shaking has ended, to allow rewriting the application according to the tracing
   * that has not been performed (e.g., rewriting of dead field instructions).
   */
  public void rewriteApplication(ExecutorService executorService) throws ExecutionException {
    // Intentionally empty.
  }
}
