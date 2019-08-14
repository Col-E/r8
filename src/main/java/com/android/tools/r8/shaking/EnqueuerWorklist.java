// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.shaking.Enqueuer.Action;
import com.google.common.collect.Queues;
import java.util.Collection;
import java.util.Queue;

public class EnqueuerWorklist {

  /** A queue of items that need processing. Different items trigger different actions. */
  private final Queue<Action> worklist = Queues.newArrayDeque();

  public void add(Action action) {
    worklist.add(action);
  }

  public void addAll(Collection<Action> action) {
    worklist.addAll(action);
  }

  public boolean isEmpty() {
    return worklist.isEmpty();
  }

  public Action poll() {
    return worklist.poll();
  }

  public void enqueueMarkReachableFieldAction(DexField field, KeepReason reason) {
    add(Action.markReachableField(field, reason));
  }
}
