// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.algorithms.scc;

import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class SCC<Node> {

  private int currentTime = 0;
  private final Reference2IntMap<Node> discoverTime = new Reference2IntOpenHashMap<>();
  private final Set<Node> unassignedSet = Sets.newIdentityHashSet();
  private final Deque<Node> unassignedStack = new ArrayDeque<>();
  private final Deque<Node> preorderStack = new ArrayDeque<>();
  private final List<Set<Node>> components = new ArrayList<>();

  private final Function<Node, Iterable<? extends Node>> successors;

  public SCC(Function<Node, Iterable<? extends Node>> successors) {
    this.successors = successors;
  }

  public List<Set<Node>> computeSCC(Node v) {
    assert currentTime == 0;
    dfs(v);
    return components;
  }

  private void dfs(Node value) {
    discoverTime.put(value, currentTime++);
    unassignedSet.add(value);
    unassignedStack.push(value);
    preorderStack.push(value);
    for (Node successor : successors.apply(value)) {
      if (!discoverTime.containsKey(successor)) {
        // If not seen yet, continue the search.
        dfs(successor);
      } else if (unassignedSet.contains(successor)) {
        // If seen already and the element is on the unassigned stack we have found a cycle.
        // Pop off everything discovered later than the target from the preorder stack. This may
        // not coincide with the cycle as an outer cycle may already have popped elements off.
        int discoverTimeOfPhi = discoverTime.getInt(successor);
        while (discoverTimeOfPhi < discoverTime.getInt(preorderStack.peek())) {
          preorderStack.pop();
        }
      }
    }
    if (preorderStack.peek() == value) {
      // If the current element is the top of the preorder stack, then we are at entry to a
      // strongly-connected component consisting of this element and every element above this
      // element on the stack.
      Set<Node> component = SetUtils.newIdentityHashSet(unassignedStack.size());
      while (true) {
        Node member = unassignedStack.pop();
        unassignedSet.remove(member);
        component.add(member);
        if (member == value) {
          components.add(component);
          break;
        }
      }
      preorderStack.pop();
    }
  }
}
