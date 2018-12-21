// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.graphinspector;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.experimental.graphinfo.ClassGraphNode;
import com.android.tools.r8.experimental.graphinfo.FieldGraphNode;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.experimental.graphinfo.GraphNode;
import com.android.tools.r8.experimental.graphinfo.MethodGraphNode;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class GraphInspector {

  private final CollectingGraphConsumer consumer;
  private final CodeInspector inspector;

  private final Set<GraphNode> roots = new HashSet<>();
  private final Map<ClassReference, ClassGraphNode> classes;
  private final Map<MethodReference, MethodGraphNode> methods;
  private final Map<FieldReference, FieldGraphNode> fields;

  public GraphInspector(CollectingGraphConsumer consumer, CodeInspector inspector) {
    this.consumer = consumer;
    this.inspector = inspector;

    Set<GraphNode> targets = consumer.getTargets();
    classes = new IdentityHashMap<>(targets.size());
    methods = new IdentityHashMap<>(targets.size());
    fields = new IdentityHashMap<>(targets.size());

    for (GraphNode target : targets) {
      if (target instanceof ClassGraphNode) {
        ClassGraphNode node = (ClassGraphNode) target;
        classes.put(node.getReference(), node);
      } else if (target instanceof MethodGraphNode) {
        MethodGraphNode node = (MethodGraphNode) target;
        methods.put(node.getReference(), node);
      } else if (target instanceof FieldGraphNode) {
        FieldGraphNode node = (FieldGraphNode) target;
        fields.put(node.getReference(), node);
      } else {
        throw new Unimplemented("Incomplet support for graph node type: " + target.getClass());
      }
      Map<GraphNode, Set<GraphEdgeInfo>> sources = consumer.getSourcesTargeting(target);
      for (GraphNode source : sources.keySet()) {
        if (!targets.contains(source)) {
          roots.add(source);
        }
      }
    }
  }

  public boolean isRenamed(MethodReference method) {
    assert isPresent(method);
    return inspector.method(method).isRenamed();
  }

  public boolean isPresent(MethodReference method) {
    if (methods.containsKey(method)) {
      assert inspector.method(method).isPresent();
      return true;
    }
    return false;
  }

  public Set<GraphNode> getRoots() {
    return Collections.unmodifiableSet(roots);
  }
}
