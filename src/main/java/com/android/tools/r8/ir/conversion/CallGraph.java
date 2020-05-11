// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CallGraphBuilderBase.CycleEliminator.CycleEliminationResult;
import com.android.tools.r8.ir.conversion.CallSiteInformation.CallGraphBasedCallSiteInformation;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Call graph representation.
 *
 * <p>Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 *
 * <p>Only methods in the program - not library methods - are represented.
 *
 * <p>The directional edges are represented as sets of nodes in each node (called methods and
 * callees).
 *
 * <p>A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 *
 * <p>Recursive calls are not present.
 */
public class CallGraph {

  public static class Node implements Comparable<Node> {

    public static Node[] EMPTY_ARRAY = {};

    private final ProgramMethod method;
    private int numberOfCallSites = 0;

    // Outgoing calls from this method.
    private final Set<Node> callees = new TreeSet<>();

    // Incoming calls to this method.
    private final Set<Node> callers = new TreeSet<>();

    // Incoming field read edges to this method (i.e., the set of methods that read a field written
    // by the current method).
    private final Set<Node> readers = new TreeSet<>();

    // Outgoing field read edges from this method (i.e., the set of methods that write a field read
    // by the current method).
    private final Set<Node> writers = new TreeSet<>();

    public Node(ProgramMethod method) {
      this.method = method;
    }

    public void addCallerConcurrently(Node caller) {
      addCallerConcurrently(caller, false);
    }

    public void addCallerConcurrently(Node caller, boolean likelySpuriousCallEdge) {
      if (caller != this && !likelySpuriousCallEdge) {
        boolean changedCallers;
        synchronized (callers) {
          changedCallers = callers.add(caller);
          numberOfCallSites++;
        }
        if (changedCallers) {
          synchronized (caller.callees) {
            caller.callees.add(this);
          }
          // Avoid redundant field read edges (call edges are considered stronger).
          removeReaderConcurrently(caller);
        }
      } else {
        synchronized (callers) {
          numberOfCallSites++;
        }
      }
    }

    public void addReaderConcurrently(Node reader) {
      if (reader != this) {
        synchronized (callers) {
          if (callers.contains(reader)) {
            // Avoid redundant field read edges (call edges are considered stronger).
            return;
          }
          boolean readersChanged;
          synchronized (readers) {
            readersChanged = readers.add(reader);
          }
          if (readersChanged) {
            synchronized (reader.writers) {
              reader.writers.add(this);
            }
          }
        }
      }
    }

    private void removeReaderConcurrently(Node reader) {
      synchronized (readers) {
        readers.remove(reader);
      }
      synchronized (reader.writers) {
        reader.writers.remove(this);
      }
    }

    public void removeCaller(Node caller) {
      boolean callersChanged = callers.remove(caller);
      assert callersChanged;
      boolean calleesChanged = caller.callees.remove(this);
      assert calleesChanged;
      assert !hasReader(caller);
    }

    public void removeReader(Node reader) {
      boolean readersChanged = readers.remove(reader);
      assert readersChanged;
      boolean writersChanged = reader.writers.remove(this);
      assert writersChanged;
      assert !hasCaller(reader);
    }

    public void cleanCalleesAndWritersForRemoval() {
      assert callers.isEmpty();
      assert readers.isEmpty();
      for (Node callee : callees) {
        boolean changed = callee.callers.remove(this);
        assert changed;
      }
      for (Node writer : writers) {
        boolean changed = writer.readers.remove(this);
        assert changed;
      }
    }

    public void cleanCallersAndReadersForRemoval() {
      assert callees.isEmpty();
      assert writers.isEmpty();
      for (Node caller : callers) {
        boolean changed = caller.callees.remove(this);
        assert changed;
      }
      for (Node reader : readers) {
        boolean changed = reader.writers.remove(this);
        assert changed;
      }
    }

    public Set<Node> getCallersWithDeterministicOrder() {
      return callers;
    }

    public Set<Node> getCalleesWithDeterministicOrder() {
      return callees;
    }

    public Set<Node> getReadersWithDeterministicOrder() {
      return readers;
    }

    public Set<Node> getWritersWithDeterministicOrder() {
      return writers;
    }

    public int getNumberOfCallSites() {
      return numberOfCallSites;
    }

    public boolean hasCallee(Node method) {
      return callees.contains(method);
    }

    public boolean hasCaller(Node method) {
      return callers.contains(method);
    }

    public boolean hasReader(Node method) {
      return readers.contains(method);
    }

    public boolean hasWriter(Node method) {
      return writers.contains(method);
    }

    public boolean isRoot() {
      return callers.isEmpty() && readers.isEmpty();
    }

    public boolean isLeaf() {
      return callees.isEmpty() && writers.isEmpty();
    }

    @Override
    public int compareTo(Node other) {
      return getProgramMethod()
          .getReference()
          .slowCompareTo(other.getProgramMethod().getReference());
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("MethodNode for: ");
      builder.append(getProgramMethod().toSourceString());
      builder.append(" (");
      builder.append(callees.size());
      builder.append(" callees, ");
      builder.append(callers.size());
      builder.append(" callers");
      builder.append(", invoke count ").append(numberOfCallSites);
      builder.append(").");
      builder.append(System.lineSeparator());
      if (callees.size() > 0) {
        builder.append("Callees:");
        builder.append(System.lineSeparator());
        for (Node call : callees) {
          builder.append("  ");
          builder.append(call.getProgramMethod().toSourceString());
          builder.append(System.lineSeparator());
        }
      }
      if (callers.size() > 0) {
        builder.append("Callers:");
        builder.append(System.lineSeparator());
        for (Node caller : callers) {
          builder.append("  ");
          builder.append(caller.getProgramMethod().toSourceString());
          builder.append(System.lineSeparator());
        }
      }
      return builder.toString();
    }

    public DexEncodedMethod getMethod() {
      return method.getDefinition();
    }

    public ProgramMethod getProgramMethod() {
      return method;
    }
  }

  final Set<Node> nodes;
  final CycleEliminationResult cycleEliminationResult;

  CallGraph(Set<Node> nodes) {
    this(nodes, null);
  }

  CallGraph(Set<Node> nodes, CycleEliminationResult cycleEliminationResult) {
    this.nodes = nodes;
    this.cycleEliminationResult = cycleEliminationResult;
  }

  static CallGraphBuilder builder(AppView<AppInfoWithLiveness> appView) {
    return new CallGraphBuilder(appView);
  }

  CallSiteInformation createCallSiteInformation(AppView<AppInfoWithLiveness> appView) {
    // Don't leverage single/dual call site information when we are not tree shaking.
    return appView.options().isShrinking()
        ? new CallGraphBasedCallSiteInformation(appView, this)
        : CallSiteInformation.empty();
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public SortedProgramMethodSet extractLeaves() {
    return extractNodes(Node::isLeaf, Node::cleanCallersAndReadersForRemoval);
  }

  public SortedProgramMethodSet extractRoots() {
    return extractNodes(Node::isRoot, Node::cleanCalleesAndWritersForRemoval);
  }

  private SortedProgramMethodSet extractNodes(Predicate<Node> predicate, Consumer<Node> clean) {
    SortedProgramMethodSet result = SortedProgramMethodSet.create();
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = nodes.iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (predicate.test(node)) {
        result.add(node.getProgramMethod());
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(clean);
    assert !result.isEmpty();
    return result;
  }
}
