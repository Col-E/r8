// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.callgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.CallGraph.CycleEliminator;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CycleEliminationTest extends TestBase {

  private static class Configuration {

    final Collection<Node> nodes;
    final Set<Node> forceInline;
    final BooleanSupplier test;

    Configuration(Collection<Node> nodes, Set<Node> forceInline, BooleanSupplier test) {
      this.nodes = nodes;
      this.forceInline = forceInline;
      this.test = test;
    }
  }

  private DexItemFactory dexItemFactory = new DexItemFactory();

  @Rule public final ExpectedException exception = ExpectedException.none();

  @Test
  public void testSimpleCycle() {
    Node method = createNode("n1");
    Node forceInlinedMethod = createForceInlinedNode("n2");

    Iterable<Collection<Node>> orderings =
        ImmutableList.of(
            ImmutableList.of(method, forceInlinedMethod),
            ImmutableList.of(forceInlinedMethod, method));

    for (Collection<Node> nodes : orderings) {
      // Create a cycle between the two nodes.
      method.addCallee(forceInlinedMethod);
      forceInlinedMethod.addCallee(method);

      // Check that the cycle eliminator finds the cycle.
      CycleEliminator cycleEliminator = new CycleEliminator(nodes, new InternalOptions());
      assertEquals(1, cycleEliminator.breakCycles());

      // The edge from method to forceInlinedMethod should be removed to ensure that force inlining
      // will work.
      assertTrue(forceInlinedMethod.isLeaf());

      // Check that the cycle eliminator agrees that there are no more cycles left.
      assertEquals(0, cycleEliminator.breakCycles());
    }
  }

  @Test
  public void testSimpleCycleWithCyclicForceInlining() {
    Node method = createForceInlinedNode("n1");
    Node forceInlinedMethod = createForceInlinedNode("n2");

    // Create a cycle between the two nodes.
    method.addCallee(forceInlinedMethod);
    forceInlinedMethod.addCallee(method);

    CycleEliminator cycleEliminator =
        new CycleEliminator(ImmutableList.of(method, forceInlinedMethod), new InternalOptions());

    exception.expect(CompilationError.class);
    exception.expectMessage(CycleEliminator.CYCLIC_FORCE_INLINING_MESSAGE);

    // Should throw because force inlining will fail.
    cycleEliminator.breakCycles();
  }

  @Test
  public void testGraphWithNestedCycles() {
    Node n1 = createNode("n1");
    Node n2 = createNode("n2");
    Node n3 = createNode("n3");

    BooleanSupplier canInlineN1 =
        () -> {
          // The node n1 should be force inlined into n2 and n3, so these edges must be kept.
          assertTrue(n2.hasCallee(n1));
          assertTrue(n3.hasCallee(n1));
          // Furthermore, the edge from n1 to n2 must be removed.
          assertFalse(n1.hasCallee(n2));
          return true;
        };

    BooleanSupplier canInlineN3 =
        () -> {
          // The node n3 should be force inlined into n2, so this edge must be kept.
          assertTrue(n2.hasCallee(n3));
          // Furthermore, one of the edges n1 -> n2 and n3 -> n1 must be removed.
          assertFalse(n1.hasCallee(n2) && n3.hasCallee(n1));
          return true;
        };

    BooleanSupplier canInlineN1N3 =
        () -> {
          // The edge n1 -> n2 must be removed.
          assertFalse(n1.hasCallee(n2));
          // Check that both can be force inlined.
          return canInlineN1.getAsBoolean() && canInlineN3.getAsBoolean();
        };

    List<Collection<Node>> orderings =
        ImmutableList.of(
            ImmutableList.of(n1, n2, n3),
            ImmutableList.of(n1, n3, n2),
            ImmutableList.of(n2, n1, n3),
            ImmutableList.of(n2, n3, n1),
            ImmutableList.of(n3, n1, n2),
            ImmutableList.of(n3, n2, n1));

    List<Configuration> configurations = new ArrayList<>();
    // All orderings, where no methods are marked as force inline.
    orderings
        .stream()
        .map(ordering -> new Configuration(ordering, ImmutableSet.of(), null))
        .forEach(configurations::add);
    // All orderings, where n1 is marked as force inline
    // (the configuration where n2 is marked as force inline is symmetric).
    orderings
        .stream()
        .map(ordering -> new Configuration(ordering, ImmutableSet.of(n1), canInlineN1))
        .forEach(configurations::add);
    // All orderings, where n3 is marked as force inline.
    orderings
        .stream()
        .map(ordering -> new Configuration(ordering, ImmutableSet.of(n3), canInlineN3))
        .forEach(configurations::add);
    // All orderings, where n1 and n3 are marked as force inline.
    orderings
        .stream()
        .map(ordering -> new Configuration(ordering, ImmutableSet.of(n1, n3), canInlineN1N3))
        .forEach(configurations::add);

    for (Configuration configuration : configurations) {
      // Create a cycle between the three nodes.
      n1.addCallee(n2);
      n2.addCallee(n3);
      n3.addCallee(n1);

      // Create a cycle in the graph between node n1 and n2.
      n2.addCallee(n1);

      for (Node node : configuration.nodes) {
        if (configuration.forceInline.contains(node)) {
          node.method.markForceInline();
        } else {
          node.method.unsetForceInline();
        }
      }

      // Check that the cycle eliminator finds the cycles.
      CycleEliminator cycleEliminator =
          new CycleEliminator(configuration.nodes, new InternalOptions());
      int numberOfCycles = cycleEliminator.breakCycles();
      if (numberOfCycles == 1) {
        // If only one cycle was removed, then it must be the edge from n1 -> n2 that was removed.
        assertTrue(n1.isLeaf());
      } else {
        // Check that the cycle eliminator found both cycles.
        assertEquals(2, numberOfCycles);
      }

      // Check that the cycle eliminator agrees that there are no more cycles left.
      assertEquals(0, cycleEliminator.breakCycles());

      // Check that force inlining is guaranteed to succeed.
      if (configuration.test != null) {
        assertTrue(configuration.test.getAsBoolean());
      }
    }
  }

  private Node createNode(String methodName) {
    DexMethod signature =
        dexItemFactory.createMethod(
            dexItemFactory.objectType,
            dexItemFactory.createProto(dexItemFactory.voidType),
            methodName);
    return new Node(new DexEncodedMethod(signature, null, null, null, null));
  }

  private Node createForceInlinedNode(String methodName) {
    Node node = createNode(methodName);
    node.method.markForceInline();
    return node;
  }
}
