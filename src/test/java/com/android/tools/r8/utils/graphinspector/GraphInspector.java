// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.graphinspector;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.ClassGraphNode;
import com.android.tools.r8.experimental.graphinfo.FieldGraphNode;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo.EdgeKind;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class GraphInspector {

  // Convenience predicates.
  public static class EdgeKindPredicate implements Predicate<Set<GraphEdgeInfo>> {
    public static final EdgeKindPredicate keepRule = new EdgeKindPredicate(EdgeKind.KeepRule);
    public static final EdgeKindPredicate invokedFrom = new EdgeKindPredicate(EdgeKind.InvokedFrom);

    private final EdgeKind edgeKind;

    public EdgeKindPredicate(EdgeKind edgeKind) {
      this.edgeKind = edgeKind;
    }

    @Override
    public boolean test(Set<GraphEdgeInfo> infos) {
      for (GraphEdgeInfo info : infos) {
        if (info.edgeKind() == edgeKind) {
          return true;
        }
      }
      return false;
    }
  }

  public interface QueryNode {
    boolean isPresent();

    boolean isRenamed();

    boolean isInvokedFrom(MethodReference method);

    // TODO(b/120959039): Add support for referencing keep rules.
    boolean isKeptByRootRule();

    String getNodeDescription();

    default String errorMessage(String expected, String actual) {
      return "Failed query on "
          + getNodeDescription()
          + ", expected: "
          + expected
          + ", got: "
          + actual;
    }

    default QueryNode assertPresent() {
      assertTrue(errorMessage("present", "absent"), isPresent());
      return this;
    }

    default QueryNode assertAbsent() {
      assertTrue(errorMessage("absent", "present"), !isPresent());
      return this;
    }

    default QueryNode assertRenamed() {
      assertTrue(errorMessage("renamed", "not-renamed"), isRenamed());
      return this;
    }

    default QueryNode assertNotRenamed() {
      assertTrue(errorMessage("not-renamed", "renamed"), !isRenamed());
      return this;
    }

    default QueryNode assertInvokedFrom(MethodReference method) {
      assertTrue(
          errorMessage("invokation from " + method.toString(), "none"), isInvokedFrom(method));
      return this;
    }

    // TODO(b/120959039): Add support for referencing keep rules.
    default QueryNode assertKeptByRootRule() {
      assertTrue(errorMessage("kept by a root rule", "none"), isKeptByRootRule());
      return this;
    }
  }

  private static class AbsentQueryNode implements QueryNode {
    private final String failedQueryNodeDescription;

    public AbsentQueryNode(String failedQueryNodeDescription) {
      this.failedQueryNodeDescription = failedQueryNodeDescription;
    }

    @Override
    public String getNodeDescription() {
      return "absent node: " + failedQueryNodeDescription;
    }

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public boolean isRenamed() {
      fail("Invalid call to isRenamed on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isInvokedFrom(MethodReference method) {
      fail("Invalid call to isInvokedFrom on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isKeptByRootRule() {
      fail("Invalid call to isKeptByRule on " + getNodeDescription());
      throw new Unreachable();
    }
  }

  // Class representing a point in the kept-graph structure.
  // The purpose of this class is to tersely specify what relationships are expected between nodes,
  // thus most methods will throw assertion errors if the predicate is false.
  private static class QueryNodeImpl implements QueryNode {

    private final GraphInspector inspector;
    private final GraphNode graphNode;

    public QueryNodeImpl(GraphInspector inspector, GraphNode graphNode) {
      this.inspector = inspector;
      this.graphNode = graphNode;
    }

    @Override
    public String getNodeDescription() {
      return graphNode.toString();
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isRenamed() {
      if (graphNode instanceof ClassGraphNode) {
        ClassGraphNode classNode = (ClassGraphNode) this.graphNode;
        return inspector.inspector.clazz(classNode.getReference()).isRenamed();
      } else if (graphNode instanceof MethodGraphNode) {
        MethodGraphNode methodNode = (MethodGraphNode) this.graphNode;
        return inspector.inspector.method(methodNode.getReference()).isRenamed();
      } else if (graphNode instanceof FieldGraphNode) {
        FieldGraphNode fieldNode = (FieldGraphNode) this.graphNode;
        return inspector.inspector.field(fieldNode.getReference()).isRenamed();
      } else {
        fail("Invalid call to isRenamed on " + getNodeDescription());
        throw new Unreachable();
      }
    }

    @Override
    public boolean isInvokedFrom(MethodReference method) {
      GraphNode sourceMethod = inspector.methods.get(method);
      if (sourceMethod == null) {
        return false;
      }
      return filterSources(
              (node, infos) -> node == sourceMethod && EdgeKindPredicate.invokedFrom.test(infos))
          .findFirst()
          .isPresent();
    }

    @Override
    public boolean isKeptByRootRule() {
      return filterSources(
              (node, infos) ->
                  inspector.getRoots().contains(node) && EdgeKindPredicate.keepRule.test(infos))
          .findFirst()
          .isPresent();
    }

    private Stream<GraphNode> filterSources(BiPredicate<GraphNode, Set<GraphEdgeInfo>> test) {
      Map<GraphNode, Set<GraphEdgeInfo>> sources =
          inspector.consumer.getSourcesTargeting(graphNode);
      return sources.entrySet().stream()
          .filter(e -> test.test(e.getKey(), e.getValue()))
          .map(Entry::getKey);
    }
  }

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

  public QueryNode method(MethodReference method) {
    return getQueryNode(methods.get(method), method.toString());
  }

  private QueryNode getQueryNode(GraphNode node, String absentString) {
    return node == null ? new AbsentQueryNode(absentString) : new QueryNodeImpl(this, node);
  }
}
