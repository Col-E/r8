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
import com.android.tools.r8.experimental.graphinfo.KeepRuleGraphNode;
import com.android.tools.r8.experimental.graphinfo.MethodGraphNode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
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

  public abstract static class QueryNode {

    abstract boolean isPresent();

    abstract boolean isRoot();

    abstract boolean isRenamed();

    abstract boolean isInvokedFrom(MethodReference method);

    abstract boolean isKeptBy(QueryNode node);

    abstract String getNodeDescription();

    protected String errorMessage(String expected, String actual) {
      return "Failed query on "
          + getNodeDescription()
          + ", expected: "
          + expected
          + ", got: "
          + actual;
    }

    public QueryNode assertPresent() {
      assertTrue(errorMessage("present", "absent"), isPresent());
      return this;
    }

    public QueryNode assertAbsent() {
      assertTrue(errorMessage("absent", "present"), !isPresent());
      return this;
    }

    public QueryNode assertRoot() {
      assertTrue(errorMessage("root", "non-root"), isRoot());
      return this;
    }

    public QueryNode assertRenamed() {
      assertTrue(errorMessage("renamed", "not-renamed"), isRenamed());
      return this;
    }

    public QueryNode assertNotRenamed() {
      assertTrue(errorMessage("not-renamed", "renamed"), !isRenamed());
      return this;
    }

    public QueryNode assertInvokedFrom(MethodReference method) {
      assertTrue(
          errorMessage("invokation from " + method.toString(), "none"), isInvokedFrom(method));
      return this;
    }

    public QueryNode assertKeptBy(QueryNode node) {
      assertTrue("Invalid call to assertKeptBy with: " + node.getNodeDescription(),
          node.isPresent());
      assertTrue(errorMessage("kept by " + getNodeDescription(), "none"), isKeptBy(node));
      return this;
    }
  }

  private static class AbsentQueryNode extends QueryNode {
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
    boolean isRoot() {
      fail("Invalid call to isRoot on " + getNodeDescription());
      throw new Unreachable();
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
    public boolean isKeptBy(QueryNode node) {
      fail("Invalid call to isKeptBy on " + getNodeDescription());
      throw new Unreachable();
    }
  }

  // Class representing a point in the kept-graph structure.
  // The purpose of this class is to tersely specify what relationships are expected between nodes,
  // thus most methods will throw assertion errors if the predicate is false.
  private static class QueryNodeImpl extends QueryNode {

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
    boolean isRoot() {
      return inspector.roots.contains(graphNode);
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
    public boolean isKeptBy(QueryNode node) {
      if (!(node instanceof QueryNodeImpl)) {
        return false;
      }
      QueryNodeImpl impl = (QueryNodeImpl) node;
      return filterSources((source, infos) -> impl.graphNode == source).findFirst().isPresent();
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
  private final Set<KeepRuleGraphNode> rules = new HashSet<>();
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
      } else if (target instanceof KeepRuleGraphNode) {
        KeepRuleGraphNode node = (KeepRuleGraphNode) target;
        rules.add(node);
      } else {
        throw new Unimplemented("Incomplet support for graph node type: " + target.getClass());
      }
      Map<GraphNode, Set<GraphEdgeInfo>> sources = consumer.getSourcesTargeting(target);
      for (GraphNode source : sources.keySet()) {
        if (!targets.contains(source)) {
          roots.add(source);
        }
        if (source instanceof KeepRuleGraphNode) {
          rules.add((KeepRuleGraphNode) source);
        }
      }
    }
  }

  public Set<GraphNode> getRoots() {
    return Collections.unmodifiableSet(roots);
  }

  public QueryNode rule(Origin origin, int line, int column) {
    String ruleReferenceString = getReferenceStringForRule(origin, line, column);
    KeepRuleGraphNode found = null;
    for (KeepRuleGraphNode rule : rules) {
      if (rule.getOrigin().equals(origin)) {
        Position position = rule.getPosition();
        if (position instanceof TextRange) {
          TextRange range = (TextRange) position;
          if (range.getStart().getLine() == line && range.getStart().getColumn() == column) {
            if (found != null) {
              fail(
                  "Found two matching rules at "
                      + ruleReferenceString
                      + ": "
                      + found
                      + " and "
                      + rule);
            }
            found = rule;
          }
        }
      }
    }
    return getQueryNode(found, ruleReferenceString);
  }

  private static String getReferenceStringForRule(Origin origin, int line, int column) {
    return "rule@" + origin + ":" + new TextPosition(0, line, column);
  }

  public QueryNode method(MethodReference method) {
    return getQueryNode(methods.get(method), method.toString());
  }

  private QueryNode getQueryNode(GraphNode node, String absentString) {
    return node == null ? new AbsentQueryNode(absentString) : new QueryNodeImpl(this, node);
  }
}
