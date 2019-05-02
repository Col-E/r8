// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.CallGraphBuilder.CycleEliminator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.IROrdering;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Call graph representation.
 * <p>
 * Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 * <p>
 * Only methods in the program - not library methods - are represented.
 * <p>
 * The directional edges are represented as sets of nodes in each node (called methods and callees).
 * <p>
 * A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 * <p>
 * Recursive calls are not present.
 */
public class CallGraph extends CallSiteInformation {

  public static class Node implements Comparable<Node> {

    public static Node[] EMPTY_ARRAY = {};

    public final DexEncodedMethod method;
    private int numberOfCallSites = 0;

    // Outgoing calls from this method.
    private final Set<Node> callees = new LinkedHashSet<>();

    // Incoming calls to this method.
    private final Set<Node> callers = new LinkedHashSet<>();

    public Node(DexEncodedMethod method) {
      this.method = method;
    }

    public boolean isBridge() {
      return method.accessFlags.isBridge();
    }

    public void addCaller(Node caller) {
      callers.add(caller);
      caller.callees.add(this);
      numberOfCallSites++;
    }

    public void removeCaller(Node caller) {
      callers.remove(caller);
      caller.callees.remove(this);
    }

    public Node[] getCalleesWithDeterministicOrder() {
      Node[] sorted = callees.toArray(Node.EMPTY_ARRAY);
      Arrays.sort(sorted);
      return sorted;
    }

    public boolean hasCallee(Node method) {
      return callees.contains(method);
    }

    public boolean hasCaller(Node method) {
      return callers.contains(method);
    }

    public boolean isLeaf() {
      return callees.isEmpty();
    }

    @Override
    public int compareTo(Node other) {
      return method.method.slowCompareTo(other.method.method);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("MethodNode for: ");
      builder.append(method.toSourceString());
      builder.append(" (");
      builder.append(callees.size());
      builder.append(" callees, ");
      builder.append(callers.size());
      builder.append(" callers");
      if (isBridge()) {
        builder.append(", bridge");
      }
      builder.append(", invoke count ").append(numberOfCallSites);
      builder.append(").\n");
      if (callees.size() > 0) {
        builder.append("Callees:\n");
        for (Node call : callees) {
          builder.append("  ");
          builder.append(call.method.toSourceString());
          builder.append("\n");
        }
      }
      if (callers.size() > 0) {
        builder.append("Callers:\n");
        for (Node caller : callers) {
          builder.append("  ");
          builder.append(caller.method.toSourceString());
          builder.append("\n");
        }
      }
      return builder.toString();
    }
  }

  private final Set<Node> nodes;
  private final IROrdering shuffle;

  private final Set<DexMethod> singleCallSite = Sets.newIdentityHashSet();
  private final Set<DexMethod> doubleCallSite = Sets.newIdentityHashSet();

  CallGraph(AppView<AppInfoWithLiveness> appView, Set<Node> nodes) {
    this.nodes = nodes;
    this.shuffle = appView.options().testing.irOrdering;

    for (Node node : nodes) {
      // For non-pinned methods we know the exact number of call sites.
      if (!appView.appInfo().isPinned(node.method.method)) {
        if (node.numberOfCallSites == 1) {
          singleCallSite.add(node.method.method);
        } else if (node.numberOfCallSites == 2) {
          doubleCallSite.add(node.method.method);
        }
      }
    }
  }

  public static CallGraphBuilder builder(AppView<AppInfoWithLiveness> appView) {
    return new CallGraphBuilder(appView);
  }

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   * <p>
   * For pinned methods (methods kept through Proguard keep rules) this will always answer
   * <code>false</code>.
   */
  @Override
  public boolean hasSingleCallSite(DexMethod method) {
    return singleCallSite.contains(method);
  }

  @Override
  public boolean hasDoubleCallSite(DexMethod method) {
    return doubleCallSite.contains(method);
  }

  /**
   * Extract the next set of leaves (nodes with an call (outgoing) degree of 0) if any.
   *
   * <p>All nodes in the graph are extracted if called repeatedly until null is returned. Please
   * note that there are no cycles in this graph (see {@link CycleEliminator#breakCycles}).
   *
   * <p>
   */
  private Collection<DexEncodedMethod> extractLeaves() {
    if (isEmpty()) {
      return Collections.emptySet();
    }
    // First identify all leaves before removing them from the graph.
    List<Node> leaves = nodes.stream().filter(Node::isLeaf).collect(Collectors.toList());
    for (Node leaf : leaves) {
      leaf.callers.forEach(caller -> caller.callees.remove(leaf));
      nodes.remove(leaf);
    }
    Set<DexEncodedMethod> methods =
        leaves.stream().map(x -> x.method).collect(Collectors.toCollection(LinkedHashSet::new));
    return shuffle.order(methods);
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   *
   * <p>As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  public <E extends Exception> void forEachMethod(
      ThrowingBiConsumer<DexEncodedMethod, Predicate<DexEncodedMethod>, E> consumer,
      Action waveStart,
      Action waveDone,
      ExecutorService executorService)
      throws ExecutionException {
    while (!isEmpty()) {
      Collection<DexEncodedMethod> methods = extractLeaves();
      assert methods.size() > 0;
      List<Future<?>> futures = new ArrayList<>();
      waveStart.execute();
      for (DexEncodedMethod method : methods) {
        futures.add(executorService.submit(() -> {
          consumer.accept(method, methods::contains);
          return null; // we want a Callable not a Runnable to be able to throw
        }));
      }
      ThreadUtils.awaitFutures(futures);
      waveDone.execute();
    }
  }
}
