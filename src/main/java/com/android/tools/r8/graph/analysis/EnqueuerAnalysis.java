// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.Timing;

public abstract class EnqueuerAnalysis {

  /** Called when a class is found to be instantiated. */
  public void processNewlyInstantiatedClass(DexProgramClass clazz, ProgramMethod context) {}

  /** Called when a class is found to be live. */
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {}

  /** Called when a field is found to be live. */
  public void processNewlyLiveField(ProgramField field) {}

  /** Called when a method is found to be live. */
  public void processNewlyLiveMethod(ProgramMethod method) {}

  /**
   * Called when the Enqueuer reaches a fixpoint. This may happen multiple times, since each
   * analysis may enqueue items into the worklist upon the fixpoint using {@param worklist}.
   */
  public void notifyFixpoint(Enqueuer enqueuer, EnqueuerWorklist worklist, Timing timing) {}

  /**
   * Called when the Enqueuer has reached the final fixpoint. Each analysis may use this callback to
   * perform some post-processing.
   */
  public void done(Enqueuer enqueuer) {}
}
