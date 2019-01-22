// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.ClassGraphNode;
import com.android.tools.r8.experimental.graphinfo.FieldGraphNode;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
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
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the whyareyoukeeping rule using an R8 kept-graph consumer.
 *
 * <p>This class is not intended for public use and clients should define their own consumer
 * instead.
 */
public class WhyAreYouKeepingConsumer extends CollectingGraphConsumer {

  // Single-linked path description when BF searching for a path.
  private static class GraphPath {
    final GraphNode node;
    final GraphPath path;

    public GraphPath(GraphNode node, GraphPath path) {
      assert node != null;
      this.node = node;
      this.path = path;
    }
  }

  public WhyAreYouKeepingConsumer(GraphConsumer subConsumer) {
    super(subConsumer);
  }

  public ClassGraphNode getClassNode(ClassReference clazz) {
    for (GraphNode node : getTargets()) {
      if (node instanceof ClassGraphNode && ((ClassGraphNode) node).getReference() == clazz) {
        return (ClassGraphNode) node;
      }
    }
    return null;
  }

  public MethodGraphNode getMethodNode(MethodReference method) {
    for (GraphNode node : getTargets()) {
      if (node instanceof MethodGraphNode && ((MethodGraphNode) node).getReference() == method) {
        return (MethodGraphNode) node;
      }
    }
    return null;
  }

  public FieldGraphNode getFieldNode(FieldReference field) {
    for (GraphNode node : getTargets()) {
      if (node instanceof FieldGraphNode && ((FieldGraphNode) node).getReference() == field) {
        return (FieldGraphNode) node;
      }
    }
    return null;
  }

  public void printWhyAreYouKeeping(ClassReference reference, PrintStream out) {
    ClassGraphNode node = getClassNode(reference);
    printWhyAreYouKeeping(node != null ? node : new ClassGraphNode(false, reference), out);
  }

  public void printWhyAreYouKeeping(MethodReference reference, PrintStream out) {
    MethodGraphNode node = getMethodNode(reference);
    printWhyAreYouKeeping(node != null ? node : new MethodGraphNode(false, reference), out);
  }

  public void printWhyAreYouKeeping(FieldReference reference, PrintStream out) {
    FieldGraphNode node = getFieldNode(reference);
    printWhyAreYouKeeping(node != null ? node : new FieldGraphNode(false, reference), out);
  }

  public void printWhyAreYouKeeping(GraphNode node, PrintStream out) {
    Formatter formatter = new Formatter(out);
    List<Pair<GraphNode, GraphEdgeInfo>> path = findShortestPathTo(node);
    if (path == null) {
      printNothingKeeping(node, out);
      return;
    }
    formatter.startItem(getNodeString(node));
    for (int i = path.size() - 1; i >= 0; i--) {
      Pair<GraphNode, GraphEdgeInfo> edge = path.get(i);
      printEdge(edge.getFirst(), edge.getSecond(), formatter);
    }
    formatter.endItem();
  }

  private void printNothingKeeping(GraphNode node, PrintStream out) {
    out.print("Nothing is keeping ");
    out.println(getNodeString(node));
  }

  private void printNothingKeeping(ClassReference clazz, PrintStream out) {
    out.print("Nothing is keeping ");
    out.println(DescriptorUtils.descriptorToJavaType(clazz.getDescriptor()));
  }

  public List<Pair<GraphNode, GraphEdgeInfo>> findShortestPathTo(final GraphNode node) {
    if (node == null) {
      return null;
    }
    Deque<GraphPath> queue;
    {
      Map<GraphNode, Set<GraphEdgeInfo>> sources = getSourcesTargeting(node);
      if (sources == null) {
        // The node is not targeted at all (it is not reachable).
        return null;
      }
      queue = new LinkedList<>();
      for (GraphNode source : sources.keySet()) {
        queue.addLast(new GraphPath(source, null));
      }
    }
    Map<GraphNode, GraphNode> seen = new IdentityHashMap<>();
    while (!queue.isEmpty()) {
      GraphPath path = queue.removeFirst();
      Map<GraphNode, Set<GraphEdgeInfo>> sources = getSourcesTargeting(path.node);
      if (sources == null) {
        return getCanonicalPath(path, node);
      }
      for (GraphNode source : sources.keySet()) {
        if (seen.containsKey(source)) {
          continue;
        }
        seen.put(source, source);
        queue.addLast(new GraphPath(source, path));
      }
    }
    throw new Unreachable("Failed to find a root from node: " + node);
  }

  // Convert a internal path representation to the external API and compute the edge reasons.
  private List<Pair<GraphNode, GraphEdgeInfo>> getCanonicalPath(
      GraphPath path, GraphNode endTarget) {
    assert path != null;
    List<Pair<GraphNode, GraphEdgeInfo>> canonical = new ArrayList<>();
    while (path.path != null) {
      GraphNode source = path.node;
      GraphNode target = path.path.node;
      Set<GraphEdgeInfo> infos = getSourcesTargeting(target).get(source);
      canonical.add(new Pair<>(source, getCanonicalInfo(infos)));
      path = path.path;
    }
    Set<GraphEdgeInfo> infos = getSourcesTargeting(endTarget).get(path.node);
    canonical.add(new Pair<>(path.node, getCanonicalInfo(infos)));
    return canonical;
  }

  // Compute the most meaningful edge reason.
  private GraphEdgeInfo getCanonicalInfo(Set<GraphEdgeInfo> infos) {
    // TODO(b/120959039): this is pretty bad...
    for (EdgeKind kind : EdgeKind.values()) {
      for (GraphEdgeInfo info : infos) {
        if (info.edgeKind() == kind) {
          return info;
        }
      }
    }
    throw new Unreachable("Unexpected empty set of graph edge info");
  }

  private void printEdge(GraphNode node, GraphEdgeInfo info, Formatter formatter) {
    formatter.addReason("is " + getInfoPrefix(info) + ":");
    addNodeMessage(node, formatter);
  }

  private String getInfoPrefix(GraphEdgeInfo info) {
    switch (info.edgeKind()) {
      case KeepRule:
      case CompatibilityRule:
        return "referenced in keep rule";
      case InstantiatedIn:
        return "instantiated in";
      case InvokedViaSuper:
        return "invoked via super from";
      case TargetedBySuper:
        return "targeted by super from";
      case InvokedFrom:
        return "invoked from";
      case InvokedFromLambdaCreatedIn:
        return "invoked from lambda created in";
      case ReferencedFrom:
        return "referenced from";
      case ReflectiveUseFrom:
        return "reflected from";
      case ReachableFromLiveType:
        return "reachable from";
      case ReferencedInAnnotation:
        return "referenced in annotation";
      case IsLibraryMethod:
        return "defined in library";
      case MethodHandleUseFrom:
        return "referenced by method handle";
      default:
        throw new Unreachable("Unexpected edge kind: " + info.edgeKind());
    }
  }

  private String getNodeString(GraphNode node) {
    if (node instanceof ClassGraphNode) {
      return DescriptorUtils.descriptorToJavaType(
          ((ClassGraphNode) node).getReference().getDescriptor());
    }
    if (node instanceof MethodGraphNode) {
      MethodReference method = ((MethodGraphNode) node).getReference();
      return (method.getReturnType() == null ? "void" : method.getReturnType().getTypeName())
          + ' '
          + method.getHolderClass().getTypeName()
          + '.'
          + method.getMethodName()
          + StringUtils.join(
              ListUtils.map(method.getFormalTypes(), TypeReference::getTypeName),
              ",",
              BraceType.PARENS);
    }
    if (node instanceof FieldGraphNode) {
      FieldReference field = ((FieldGraphNode) node).getReference();
      return field.getFieldType().getTypeName()
          + ' '
          + field.getHolderClass().getTypeName()
          + '.'
          + field.getFieldName();
    }
    if (node instanceof KeepRuleGraphNode) {
      KeepRuleGraphNode keepRuleNode = (KeepRuleGraphNode) node;
      return keepRuleNode.getOrigin() == Origin.unknown()
          ? keepRuleNode.getContent()
          : keepRuleNode.getOrigin() + ":" + shortPositionInfo(keepRuleNode.getPosition());
    }
    throw new Unreachable("Unexpected graph node type: " + node);
  }

  private void addNodeMessage(GraphNode node, Formatter formatter) {
    for (String line : StringUtils.splitLines(getNodeString(node))) {
      formatter.addMessage(line);
    }
  }

  private static String shortPositionInfo(Position position) {
    if (position instanceof TextRange) {
      TextPosition start = ((TextRange) position).getStart();
      return start.getLine() + ":" + start.getColumn();
    }
    return position.getDescription();
  }

  private static class Formatter {
    private final PrintStream output;
    private int indentation = -1;

    public Formatter(PrintStream output) {
      this.output = output;
    }

    void startItem(String itemString) {
      indentation++;
      indent();
      output.println(itemString);
    }

    private void indent() {
      for (int i = 0; i < indentation; i++) {
        output.print("  ");
      }
    }

    void addReason(String thing) {
      indent();
      output.print("|- ");
      output.println(thing);
    }

    void addMessage(String thing) {
      indent();
      output.print("|  ");
      output.println(thing);
    }

    void endItem() {
      indentation--;
    }
  }
}
