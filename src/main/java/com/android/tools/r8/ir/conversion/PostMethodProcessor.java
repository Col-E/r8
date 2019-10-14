// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class PostMethodProcessor {

  /**
   * Extract the next set of roots (nodes with an incoming call degree of 0) if any.
   *
   * <p>All nodes in the graph are extracted if called repeatedly until null is returned.
   */
  static void extractRoots(Iterable<Node> nodes, Consumer<Node> fn) {
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = nodes.iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (node.isRoot()) {
        fn.accept(node);
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(Node::cleanCalleesForRemoval);
  }
}
