// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.origin.Origin;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DesugarGraphTestConsumer implements DesugarGraphConsumer {

  private Map<Origin, Set<Origin>> edges = new HashMap<>();

  public boolean contains(Origin dependency, Origin dependent) {
    return edges.getOrDefault(dependency, Collections.emptySet()).contains(dependent);
  }

  public int totalEdgeCount() {
    int count = 0;
    for (Set<Origin> dependents : edges.values()) {
      count += dependents.size();
    }
    return count;
  }

  @Override
  public synchronized void accept(Origin dependent, Origin dependency) {
    edges.computeIfAbsent(dependency, s -> new HashSet<>()).add(dependent);
  }
}
