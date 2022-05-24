// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class EnqueuerDeferredTracing {

  public static EnqueuerDeferredTracing create(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer, Mode mode) {
    if (mode.isInitialTreeShaking()) {
      return empty();
    }
    InternalOptions options = appView.options();
    if (!options.isOptimizing()
        || !options.isShrinking()
        || !options.enableEnqueuerDeferredTracing) {
      return empty();
    }
    return new EnqueuerDeferredTracingImpl(appView, enqueuer, mode);
  }

  public static EmptyEnqueuerDeferredTracing empty() {
    return new EmptyEnqueuerDeferredTracing();
  }

  /**
   * @return true if the {@link Enqueuer} should not trace the given field reference.
   *     <p>If for some reason the field reference should be traced after all, a worklist item will
   *     be enqueued upon reaching a (preliminary) fixpoint in {@link
   *     #enqueueWorklistActions(EnqueuerWorklist)}, which will cause tracing to continue.
   */
  public abstract boolean deferTracingOfFieldAccess(
      DexField fieldReference,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      FieldAccessKind accessKind,
      FieldAccessMetadata metadata);

  /**
   * Called when the {@link EnqueuerWorklist} is empty, to allow additional tracing before ending
   * tree shaking.
   *
   * @return true if any worklist items were enqueued.
   */
  public abstract boolean enqueueWorklistActions(EnqueuerWorklist worklist);

  /**
   * Called when tree shaking has ended, to allow rewriting the application according to the tracing
   * that has not been performed (e.g., rewriting of dead field instructions).
   */
  public abstract void rewriteApplication(ExecutorService executorService)
      throws ExecutionException;

  public abstract void notifyReflectiveFieldAccess(ProgramField field, ProgramMethod context);
}
