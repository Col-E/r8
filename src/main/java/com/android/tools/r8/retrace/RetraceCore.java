// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RetraceCore {

  public static class StackTraceNode {
    private final List<StackTraceNode> children = new ArrayList<>();
    // TODO(b/132850880): This is not the final design of a node, but we need a placeholder for
    //  writing tests.
    public String line;

    @Override
    public String toString() {
      return line;
    }
  }

  public static class RetraceResult {

    StackTraceNode root;

    RetraceResult(StackTraceNode root) {
      this.root = root;
    }

    public List<String> toList() {
      ArrayList<String> stackTrace = new ArrayList<>();
      if (root == null) {
        return stackTrace;
      }
      Deque<StackTraceNode> nodes = new ArrayDeque<>();
      nodes.addLast(root);
      while (!nodes.isEmpty()) {
        StackTraceNode currentNode = nodes.removeFirst();
        stackTrace.add(currentNode.line);
        for (StackTraceNode child : currentNode.children) {
          assert child != null;
          nodes.addLast(child);
        }
      }
      return stackTrace;
    }
  }

  private final ClassNameMapper classNameMapper;
  private final List<String> stackTrace;
  private final DiagnosticsHandler diagnosticsHandler;

  public RetraceCore(
      ClassNameMapper classNameMapper,
      List<String> stackTrace,
      DiagnosticsHandler diagnosticsHandler) {
    this.classNameMapper = classNameMapper;
    this.stackTrace = stackTrace;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  public RetraceResult retrace() {
    return new RetraceResult(retraceLine(stackTrace, 0));
  }

  private StackTraceNode retraceLine(List<String> stackTrace, int index) {
    if (stackTrace.size() <= index) {
      return null;
    }
    StackTraceNode node = new StackTraceNode();
    node.line = stackTrace.get(index);
    StackTraceNode childNode = retraceLine(stackTrace, index + 1);
    if (childNode != null) {
      node.children.add(childNode);
    }
    return node;
  }
}
