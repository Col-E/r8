// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.environmentdependence;

import com.android.tools.r8.algorithms.scc.SCC;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A directed graph where the nodes are values.
 *
 * <ol>
 *   <li>An edge from a value v to another value v' specifies that if v' may depend on the
 *       environment then v may also depend on the environment.
 *   <li>If a value v has no outgoing edges, then it does not depend on the environment.
 * </ol>
 */
public class ValueGraph {

  private final Map<Value, Node> nodes = new IdentityHashMap<>();

  public Node createNodeIfAbsent(Value value) {
    return nodes.computeIfAbsent(value, Node::new);
  }

  public void addDirectedEdge(Node from, Node to) {
    to.predecessors.add(from);
    from.successors.add(to);
  }

  public Collection<Node> getNodes() {
    return nodes.values();
  }

  public void mergeNodes(Iterable<Node> iterable) {
    Iterator<Node> iterator = iterable.iterator();
    assert iterator.hasNext();
    Node primary = iterator.next();
    while (iterator.hasNext()) {
      Node secondary = iterator.next();
      secondary.moveEdgesTo(primary);
      primary.addLabel(secondary.label);
      nodes.put(secondary.value, primary);
    }
  }

  public void mergeStronglyConnectedComponents() {
    WorkList<Node> worklist = WorkList.newIdentityWorkList(nodes.values());
    while (worklist.hasNext()) {
      Node node = worklist.next();
      List<Set<Node>> components = new SCC<>(Node::getSuccessors).computeSCC(node);
      for (Set<Node> component : components) {
        mergeNodes(component);
        worklist.markAsSeen(component);
      }
    }
  }

  public static class Node {

    private final Value value;

    private final Set<Value> label = Sets.newIdentityHashSet();
    private final Set<Node> predecessors = Sets.newIdentityHashSet();
    private final Set<Node> successors = Sets.newIdentityHashSet();

    public Node(Value value) {
      this.label.add(value);
      this.value = value;
    }

    public void addLabel(Set<Value> label) {
      this.label.addAll(label);
    }

    public Set<Node> getSuccessors() {
      return successors;
    }

    public boolean hasSuccessorThatMatches(Predicate<Node> predicate) {
      for (Node successor : successors) {
        if (predicate.test(successor)) {
          return true;
        }
      }
      return false;
    }

    public void moveEdgesTo(Node node) {
      for (Node predecessor : predecessors) {
        predecessor.successors.remove(this);
        predecessor.successors.add(node);
        node.predecessors.add(predecessor);
      }
      predecessors.clear();
      for (Node successor : successors) {
        successor.predecessors.remove(this);
        successor.predecessors.add(node);
        node.successors.add(successor);
      }
      successors.clear();
    }
  }
}
