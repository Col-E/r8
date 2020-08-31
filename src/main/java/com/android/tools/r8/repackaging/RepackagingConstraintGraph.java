// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * An undirected graph that contains a node for each class, field, and method in a given package.
 *
 * <p>An edge X <-> Y is added if X contains a reference to Y and Y is only accessible to X if X and
 * Y are in the same package.
 *
 * <p>Once the graph is populated, we compute the set of reachable nodes from the set of root nodes
 * that cannot be repackaged due to a -keep rule. The remaining nodes in the graph can all be
 * repackaged.
 */
public class RepackagingConstraintGraph {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramPackage pkg;
  private final Map<DexDefinition, Node> nodes = new IdentityHashMap<>();
  private final Set<Node> pinnedNodes = Sets.newIdentityHashSet();

  public RepackagingConstraintGraph(AppView<AppInfoWithLiveness> appView, ProgramPackage pkg) {
    this.appView = appView;
    this.pkg = pkg;
  }

  /** Returns true if all classes in the package can be repackaged. */
  public boolean initializeGraph() {
    // Add all the items in the package into the graph. This way we know which items belong to the
    // package without having to extract package descriptor strings and comparing them with the
    // package descriptor.
    boolean hasPinnedItem = false;
    for (DexProgramClass clazz : pkg) {
      boolean isPinned = !appView.appInfo().isMinificationAllowed(clazz.getType());
      Node classNode = createNode(clazz);
      if (isPinned) {
        pinnedNodes.add(classNode);
      }
      for (DexEncodedMember<?, ?> member : clazz.members()) {
        Node memberNode = createNode(member);
        classNode.addNeighbor(memberNode);
      }
      hasPinnedItem |= isPinned;
    }
    return !hasPinnedItem;
  }

  private Node createNode(DexDefinition definition) {
    Node node = new Node(definition);
    nodes.put(definition, node);
    return node;
  }

  Node getNode(DexDefinition definition) {
    return nodes.get(definition);
  }

  public void populateConstraints(ExecutorService executorService) throws ExecutionException {
    // Concurrently add references from methods to the graph.
    ThreadUtils.processItems(
        pkg::forEachMethod, this::registerReferencesFromMethod, executorService);

    // TODO(b/165783399): Evaluate if it is worth to parallelize this. The work per field and class
    //  should be little, so it may not be.
    pkg.forEachClass(this::registerReferencesFromClass);
    pkg.forEachField(this::registerReferencesFromField);
  }

  private void registerReferencesFromClass(DexProgramClass clazz) {
    // TODO(b/165783399): Trace the references to the immediate super types.
    // TODO(b/165783399): Maybe trace the references in the nest host and/or members.
    // TODO(b/165783399): Maybe trace the references to the inner classes.
    // TODO(b/165783399): Maybe trace the references in @kotlin.Metadata.
  }

  private void registerReferencesFromField(ProgramField field) {
    // TODO(b/165783399): Trace the type of the field.
    // TODO(b/165783399): Trace the references in the field annotations.
  }

  private void registerReferencesFromMethod(ProgramMethod method) {
    // TODO(b/165783399): Trace the type references in the method signature.
    // TODO(b/165783399): Trace the references in the method and method parameter annotations.
    DexEncodedMethod definition = method.getDefinition();
    if (definition.hasCode()) {
      RepackagingUseRegistry registry = new RepackagingUseRegistry(appView, this, method);
      definition.getCode().registerCodeReferences(method, registry);
    }
  }

  public Iterable<DexProgramClass> computeClassesToRepackage() {
    WorkList<Node> worklist = WorkList.newIdentityWorkList(pinnedNodes);
    while (worklist.hasNext()) {
      worklist.addIfNotSeen(worklist.next().getNeighbors());
    }
    Set<Node> pinnedNodes = worklist.getSeenSet();
    List<DexProgramClass> classesToRepackage = new ArrayList<>();
    for (DexProgramClass clazz : pkg) {
      if (!pinnedNodes.contains(getNode(clazz))) {
        classesToRepackage.add(clazz);
      }
    }
    return classesToRepackage;
  }

  static class Node {

    private final DexDefinition definition;

    private final Set<Node> neighbors = Sets.newConcurrentHashSet();

    private Node(DexDefinition definition) {
      this.definition = definition;
    }

    public void addNeighbor(Node neighbor) {
      neighbors.add(neighbor);
      neighbor.neighbors.add(this);
    }

    public Set<Node> getNeighbors() {
      return neighbors;
    }
  }
}
