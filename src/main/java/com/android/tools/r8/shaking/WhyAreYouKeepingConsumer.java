// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graphinfo.ClassGraphNode;
import com.android.tools.r8.graphinfo.FieldGraphNode;
import com.android.tools.r8.graphinfo.GraphConsumer;
import com.android.tools.r8.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.graphinfo.GraphEdgeInfo.EdgeKind;
import com.android.tools.r8.graphinfo.GraphNode;
import com.android.tools.r8.graphinfo.KeepRuleGraphNode;
import com.android.tools.r8.graphinfo.MethodGraphNode;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WhyAreYouKeepingConsumer implements GraphConsumer {

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

  // Possible sub-consumer that is also inspecting the kept-graph.
  private final GraphConsumer subConsumer;

  // Directional map backwards from targets to direct sources.
  private final Map<GraphNode, Map<GraphNode, Set<GraphEdgeInfo>>> target2sources =
      new IdentityHashMap<>();

  public WhyAreYouKeepingConsumer(GraphConsumer subConsumer) {
    this.subConsumer = subConsumer;
  }

  @Override
  public void acceptEdge(GraphNode source, GraphNode target, GraphEdgeInfo info) {
    target2sources
        .computeIfAbsent(target, k -> new IdentityHashMap<>())
        .computeIfAbsent(source, k -> new HashSet<>())
        .add(info);
    if (subConsumer != null) {
      subConsumer.acceptEdge(source, target, info);
    }
  }

  /** Print the shortest path from a root to a node in the graph. */
  public void printWhyAreYouKeeping(String descriptor, PrintStream out) {
    assert DescriptorUtils.isClassDescriptor(descriptor);
    for (GraphNode node : target2sources.keySet()) {
      if (node.identity().equals(descriptor)) {
        printWhyAreYouKeeping(node, out);
        return;
      }
    }
    printNothingKeeping(descriptor, out);
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

  private void printNothingKeeping(String descriptor, PrintStream out) {
    out.print("Nothing is keeping ");
    out.println(DescriptorUtils.descriptorToJavaType(descriptor));
  }

  private List<Pair<GraphNode, GraphEdgeInfo>> findShortestPathTo(final GraphNode node) {
    Deque<GraphPath> queue;
    {
      Map<GraphNode, Set<GraphEdgeInfo>> sources = target2sources.get(node);
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
      Map<GraphNode, Set<GraphEdgeInfo>> sources = target2sources.get(path.node);
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
      Set<GraphEdgeInfo> infos = target2sources.get(target).get(source);
      canonical.add(new Pair<>(source, getCanonicalInfo(infos)));
      path = path.path;
    }
    Set<GraphEdgeInfo> infos = target2sources.get(endTarget).get(path.node);
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
      case ReachableFromLiveType:
        return "reachable from";
      case ReferencedInAnnotation:
        return "referenced in annotation";
      case IsLibraryMethod:
        return "defined in library";
      default:
        throw new Unreachable("Unexpected edge kind: " + info.edgeKind());
    }
  }

  private String getNodeString(GraphNode node) {
    if (node instanceof ClassGraphNode) {
      return DescriptorUtils.descriptorToJavaType(((ClassGraphNode) node).getDescriptor());
    }
    if (node instanceof MethodGraphNode) {
      MethodGraphNode methodNode = (MethodGraphNode) node;
      MethodSignature signature =
          MethodSignature.fromSignature(
              methodNode.getMethodName(), methodNode.getMethodDescriptor());
      return signature.type
          + ' '
          + DescriptorUtils.descriptorToJavaType(methodNode.getHolderDescriptor())
          + '.'
          + methodNode.getMethodName()
          + StringUtils.join(Arrays.asList(signature.parameters), ",", BraceType.PARENS);
    }
    if (node instanceof FieldGraphNode) {
      FieldGraphNode fieldNode = (FieldGraphNode) node;
      return DescriptorUtils.descriptorToJavaType(fieldNode.getFieldDescriptor())
          + ' '
          + DescriptorUtils.descriptorToJavaType(fieldNode.getHolderDescriptor())
          + '.'
          + fieldNode.getFieldName();
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
