// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.origin.Origin;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DesugarGraphTestConsumer implements DesugarGraphConsumer {

  private boolean finished = false;
  private Map<Origin, Set<Origin>> edges = new HashMap<>();

  public boolean contains(Origin dependency, Origin dependent) {
    assertTrue(finished);
    return edges.getOrDefault(dependency, Collections.emptySet()).contains(dependent);
  }

  public int totalEdgeCount() {
    assertTrue(finished);
    int count = 0;
    for (Set<Origin> dependents : edges.values()) {
      count += dependents.size();
    }
    return count;
  }

  @Override
  public synchronized void accept(Origin dependent, Origin dependency) {
    assertFalse(finished);
    edges.computeIfAbsent(dependency, s -> new HashSet<>()).add(dependent);
  }

  @Override
  public void finished() {
    assertFalse(finished);
    finished = true;
  }
}
