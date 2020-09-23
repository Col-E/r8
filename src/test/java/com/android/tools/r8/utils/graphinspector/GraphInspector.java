// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.graphinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.Assert;

public class GraphInspector {

  // Convenience predicates.
  public static class EdgeKindPredicate implements Predicate<Set<GraphEdgeInfo>> {
    public static final EdgeKindPredicate keepRule = new EdgeKindPredicate(EdgeKind.KeepRule);
    public static final EdgeKindPredicate invokedFrom = new EdgeKindPredicate(EdgeKind.InvokedFrom);
    public static final EdgeKindPredicate reflectedFrom =
        new EdgeKindPredicate(EdgeKind.ReflectiveUseFrom);
    public static final EdgeKindPredicate isLibraryMethod =
        new EdgeKindPredicate(EdgeKind.IsLibraryMethod);
    public static final EdgeKindPredicate overriding =
        new EdgeKindPredicate(EdgeKind.OverridingMethod);
    public static final EdgeKindPredicate compatibilityRule =
        new EdgeKindPredicate(EdgeKind.CompatibilityRule);

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

  public static class QueryNodeSet {
    private final Set<QueryNode> nodes;
    private final String absentString;

    public QueryNodeSet(Set<QueryNode> nodes, String absentString) {
      this.nodes = nodes;
      this.absentString = absentString;
    }

    private static QueryNodeSet from(Set<QueryNode> nodes, String absentString) {
      return new QueryNodeSet(nodes, absentString);
    }

    private String errorMessage(String expected, String actual) {
      return "Expected " + expected + " but was " + actual + " for " + absentString;
    }

    public boolean isEmpty() {
      return nodes.isEmpty();
    }

    public QueryNodeSet assertEmpty() {
      assertTrue(errorMessage("empty", "non-empty"), isEmpty());
      return this;
    }

    public QueryNodeSet assertNonEmpty() {
      assertFalse(errorMessage("non-empty", "empty"), isEmpty());
      return this;
    }

    public QueryNodeSet assertSize(int expected) {
      assertEquals(errorMessage("" + expected, "" + nodes.size()), expected, nodes.size());
      return this;
    }

    public QueryNodeSet assertAnyMatch(Predicate<QueryNode> predicate) {
      assertTrue(nodes.stream().anyMatch(predicate));
      return this;
    }

    public QueryNodeSet assertAllMatch(Predicate<QueryNode> predicate) {
      assertTrue(nodes.stream().allMatch(predicate));
      return this;
    }
  }

  public abstract static class QueryNode {

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public abstract boolean isPresent();

    public abstract boolean isRoot();

    public abstract boolean isRenamed();

    public abstract boolean isInvokedFrom(MethodReference method);

    public abstract boolean isReflectedFrom(MethodReference method);

    public abstract boolean isOverriding(MethodReference method);

    public abstract boolean isKeptBy(QueryNode node);

    public abstract boolean isCompatKeptBy(QueryNode node);

    public abstract boolean isPureCompatKeptBy(QueryNode node);

    public abstract boolean isKeptByLibraryMethod(QueryNode node);

    public abstract boolean isSatisfiedBy(QueryNode... nodes);

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

    public QueryNode assertNotRoot() {
      assertFalse(errorMessage("non-root", "root"), isRoot());
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
          errorMessage("invocation from " + method.toString(), "none"), isInvokedFrom(method));
      return this;
    }

    public QueryNode assertNotInvokedFrom(MethodReference method) {
      assertFalse(
          errorMessage("no invocation from " + method.toString(), "invoke"), isInvokedFrom(method));
      return this;
    }

    public QueryNode assertReflectedFrom(MethodReference method) {
      assertTrue(
          errorMessage("reflection from " + method.toString(), "none"), isReflectedFrom(method));
      return this;
    }

    public QueryNode assertNotReflectedFrom(MethodReference method) {
      assertFalse(
          errorMessage("no reflection from " + method.toString(), "reflection"),
          isReflectedFrom(method));
      return this;
    }

    public QueryNode assertOverriding(MethodReference method) {
      assertTrue(errorMessage("overriding " + method.toString(), "none"), isOverriding(method));
      return this;
    }

    public QueryNode assertKeptBy(QueryNode node) {
      assertTrue(
          "Invalid call to assertKeptBy with: " + node.getNodeDescription(), node.isPresent());
      assertTrue(
          errorMessage("kept by " + node.getNodeDescription(), "was not kept by it"),
          isKeptBy(node));
      return this;
    }

    public QueryNode assertNotKeptBy(QueryNode node) {
      assertTrue(
          "Invalid call to assertNotKeptBy with: " + node.getNodeDescription(), node.isPresent());
      assertFalse(
          errorMessage("not kept by " + node.getNodeDescription(), "was kept by it"),
          isKeptBy(node));
      return this;
    }

    public QueryNode assertCompatKeptBy(QueryNode node) {
      assertTrue(
          "Invalid call to assertCompatKeptBy with: " + node.getNodeDescription(),
          node.isPresent());
      assertTrue(
          errorMessage("compat kept by " + node.getNodeDescription(), "was not kept by it"),
          isCompatKeptBy(node));
      return this;
    }

    public QueryNode assertNotCompatKeptBy(QueryNode node) {
      assertTrue(
          "Invalid call to assertNotKeptBy with: " + node.getNodeDescription(), node.isPresent());
      assertFalse(
          errorMessage("not kept by " + node.getNodeDescription(), "was kept by it"),
          isCompatKeptBy(node));
      return this;
    }

    public QueryNode assertPureCompatKeptBy(QueryNode node) {
      assertTrue(
          "Invalid call to assertPureCompatKeptBy with: " + node.getNodeDescription(),
          node.isPresent());
      assertTrue(
          errorMessage("compat kept by " + node.getNodeDescription(), "was not kept by it"),
          isPureCompatKeptBy(node));
      return this;
    }

    public QueryNode assertSatisfiedBy(QueryNode... nodes) {
      if (isSatisfiedBy(nodes)) {
        return this;
      }
      QueryNodeImpl impl = (QueryNodeImpl) this;
      impl.runSatisfiedBy(Assert::fail, nodes);
      throw new Unreachable();
    }

    public QueryNode assertKeptByLibraryMethod(QueryNode node) {
      assertTrue(
          "Invalid call to assertKeptBy with: " + node.getNodeDescription(), node.isPresent());
      assertTrue(
          errorMessage(
              "kept by library method on " + node.getNodeDescription(),
              "was not kept by a library method"),
          isKeptByLibraryMethod(node));
      return this;
    }

    public abstract String getKeptGraphString();
  }

  private static class AbsentQueryNode extends QueryNode {
    private final String failedQueryNodeDescription;

    public AbsentQueryNode(String failedQueryNodeDescription) {
      assert failedQueryNodeDescription != null;
      this.failedQueryNodeDescription = failedQueryNodeDescription;
    }

    @Override
    public String getKeptGraphString() {
      return "<not kept>";
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof AbsentQueryNode
          && failedQueryNodeDescription.equals(((AbsentQueryNode) obj).failedQueryNodeDescription);
    }

    @Override
    public int hashCode() {
      return failedQueryNodeDescription.hashCode();
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
    public boolean isRoot() {
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
    public boolean isReflectedFrom(MethodReference method) {
      fail("Invalid call to isReflectedFrom on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isOverriding(MethodReference method) {
      fail("Invalid call to isOverriding on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isKeptBy(QueryNode node) {
      fail("Invalid call to isKeptBy on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isCompatKeptBy(QueryNode node) {
      fail("Invalid call to isCompatKeptBy on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isPureCompatKeptBy(QueryNode node) {
      fail("Invalid call to isPureCompatKeptBy on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isKeptByLibraryMethod(QueryNode node) {
      fail("Invalid call to isKeptByLibrary on " + getNodeDescription());
      throw new Unreachable();
    }

    @Override
    public boolean isSatisfiedBy(QueryNode... nodes) {
      fail("Invalid call to isTriggeredBy on " + getNodeDescription());
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
    public boolean equals(Object obj) {
      return obj instanceof QueryNodeImpl && graphNode.equals(((QueryNodeImpl) obj).graphNode);
    }

    @Override
    public int hashCode() {
      return graphNode.hashCode();
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
    public boolean isRoot() {
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
    public boolean isReflectedFrom(MethodReference method) {
      GraphNode sourceMethod = inspector.methods.get(method);
      if (sourceMethod == null) {
        return false;
      }
      return filterSources(
              (node, infos) -> node == sourceMethod && EdgeKindPredicate.reflectedFrom.test(infos))
          .findFirst()
          .isPresent();
    }

    @Override
    public boolean isOverriding(MethodReference method) {
      GraphNode sourceMethod = inspector.methods.get(method);
      if (sourceMethod == null) {
        return false;
      }
      return filterSources(
              (node, infos) -> node == sourceMethod && EdgeKindPredicate.overriding.test(infos))
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

    @Override
    public boolean isCompatKeptBy(QueryNode node) {
      if (!(node instanceof QueryNodeImpl)) {
        return false;
      }
      QueryNodeImpl impl = (QueryNodeImpl) node;
      return filterSources(
              (source, infos) ->
                  impl.graphNode == source && EdgeKindPredicate.compatibilityRule.test(infos))
          .findFirst()
          .isPresent();
    }

    @Override
    public boolean isPureCompatKeptBy(QueryNode node) {
      if (!isCompatKeptBy(node)) {
        return false;
      }
      QueryNodeImpl impl = (QueryNodeImpl) node;
      return filterSources((source, infos) -> impl.graphNode != source).count() == 0;
    }

    @Override
    public boolean isKeptByLibraryMethod(QueryNode node) {
      assert graphNode instanceof MethodGraphNode;
      if (!(node instanceof QueryNodeImpl)) {
        return false;
      }
      QueryNodeImpl impl = (QueryNodeImpl) node;
      return filterSources(
              (source, infos) ->
                  impl.graphNode == source && EdgeKindPredicate.isLibraryMethod.test(infos))
          .findFirst()
          .isPresent();
    }

    @Override
    public boolean isSatisfiedBy(QueryNode... nodes) {
      Box<Boolean> box = new Box<>(true);
      runSatisfiedBy(ignore -> box.set(false), nodes);
      return box.get();
    }

    private void runSatisfiedBy(Consumer<String> onError, QueryNode[] nodes) {
      assertTrue(
          "Invalid call to isTriggeredBy on non-keep rule node: " + graphNode,
          graphNode instanceof KeepRuleGraphNode);
      Set<GraphNode> preconditions = ((KeepRuleGraphNode) graphNode).getPreconditions();
      for (QueryNode node : nodes) {
        if (!(node instanceof QueryNodeImpl)) {
          onError.accept(
              "Expected query of precondition to be present, but it was not. "
                  + "Precondtion node: "
                  + node.getNodeDescription());
          return;
        }
        QueryNodeImpl impl = (QueryNodeImpl) node;
        if (!filterSources((source, infos) -> impl.graphNode == source).findFirst().isPresent()) {
          onError.accept(
              "Expected to find dependency from precondtion to dependent rule, but could not. "
                  + "Precondition node: "
                  + node.getNodeDescription());
          return;
        }
        if (!preconditions.contains(impl.graphNode)) {
          onError.accept(
              "Expected precondition set to contain node "
                  + node.getNodeDescription()
                  + ", but it did not.");
          return;
        }
      }
      assert preconditions.size() >= nodes.length;
      if (nodes.length != preconditions.size()) {
        for (GraphNode precondition : preconditions) {
          if (Arrays.stream(nodes)
              .noneMatch(node -> ((QueryNodeImpl) node).graphNode == precondition)) {
            onError.accept("Unexpected item in precondtions: " + precondition.toString());
            return;
          }
        }
        throw new Unreachable();
      }
    }

    @Override
    public String getKeptGraphString() {
      StringBuilder builder = new StringBuilder();
      getKeptGraphString(graphNode, inspector, builder, "", ImmutableSet.of());
      return builder.toString();
    }

    private static void getKeptGraphString(
        GraphNode graphNode,
        GraphInspector inspector,
        StringBuilder builder,
        String indent,
        Set<GraphNode> seen) {
      builder.append(graphNode);
      if (seen.contains(graphNode)) {
        builder.append(" <CYCLE>");
        return;
      }
      seen = ImmutableSet.<GraphNode>builder().addAll(seen).add(graphNode).build();
      Map<GraphNode, Set<GraphEdgeInfo>> sources =
          inspector.consumer.getSourcesTargeting(graphNode);
      if (sources == null) {
        builder.append(" <ROOT>");
        return;
      }
      for (Entry<GraphNode, Set<GraphEdgeInfo>> entry : sources.entrySet()) {
        GraphNode source = entry.getKey();
        Set<GraphEdgeInfo> reasons = entry.getValue();
        builder.append('\n').append(indent).append("<- ");
        getKeptGraphString(source, inspector, builder, indent + "  ", seen);
      }
    }

    private Stream<GraphNode> filterSources(BiPredicate<GraphNode, Set<GraphEdgeInfo>> test) {
      Map<GraphNode, Set<GraphEdgeInfo>> sources =
          inspector.consumer.getSourcesTargeting(graphNode);
      assertNotNull("Attempt to iterate sources of apparent root node: " + graphNode, sources);
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
    classes = new HashMap<>(targets.size());
    methods = new HashMap<>(targets.size());
    fields = new HashMap<>(targets.size());

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

  public CodeInspector codeInspector() {
    return inspector;
  }

  public Set<GraphNode> getRoots() {
    return Collections.unmodifiableSet(roots);
  }

  public QueryNode rule(String ruleContent) {
    KeepRuleGraphNode found = null;
    for (KeepRuleGraphNode rule : rules) {
      if (rule.getContent().equals(ruleContent)) {
        if (found != null) {
          fail("Found two matching rules matching " + ruleContent + ": " + found + " and " + rule);
        }
        found = rule;
      }
    }
    return getQueryNode(found, ruleContent);
  }

  public QueryNodeSet ruleInstances(String ruleContent) {
    Set<QueryNode> set = new HashSet<>();
    for (KeepRuleGraphNode rule : rules) {
      if (rule.getContent().equals(ruleContent)) {
        set.add(getQueryNode(rule, ruleContent));
      }
    }
    return QueryNodeSet.from(set, ruleContent);
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

  public QueryNode clazz(ClassReference clazz) {
    return getQueryNode(classes.get(clazz), clazz.toString());
  }

  public QueryNode method(MethodReference method) {
    return getQueryNode(methods.get(method), method.toString());
  }

  public QueryNode field(FieldReference field) {
    return getQueryNode(fields.get(field), field.toString());
  }

  private QueryNode getQueryNode(GraphNode node, String absentString) {
    return node == null ? new AbsentQueryNode(absentString) : new QueryNodeImpl(this, node);
  }

  private boolean isPureCompatTarget(GraphNode target) {
    Map<GraphNode, Set<GraphEdgeInfo>> sources = consumer.getSourcesTargeting(target);
    if (sources == null || sources.isEmpty()) {
      return false;
    }
    for (Entry<GraphNode, Set<GraphEdgeInfo>> edge : sources.entrySet()) {
      for (GraphEdgeInfo edgeInfo : edge.getValue()) {
        if (edgeInfo.edgeKind() != EdgeKind.CompatibilityRule) {
          return false;
        }
      }
    }
    return true;
  }

  public void assertNoPureCompatibilityEdges() {
    for (GraphNode target : consumer.getTargets()) {
      assertFalse(isPureCompatTarget(target));
    }
  }
}
