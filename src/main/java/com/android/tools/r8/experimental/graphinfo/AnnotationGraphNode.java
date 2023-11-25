// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.graphinfo;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.Objects;

@KeepForApi
public final class AnnotationGraphNode extends GraphNode {

  private final GraphNode annotatedNode;
  private final ClassGraphNode annotationClassNode;

  public AnnotationGraphNode(GraphNode annotatedNode, ClassGraphNode annotationClassNode) {
    super(annotatedNode.isLibraryNode());
    this.annotatedNode = annotatedNode;
    this.annotationClassNode = annotationClassNode;
  }

  public GraphNode getAnnotatedNode() {
    return annotatedNode;
  }

  public ClassGraphNode getAnnotationClassNode() {
    return annotationClassNode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AnnotationGraphNode)) {
      return false;
    }
    AnnotationGraphNode node = (AnnotationGraphNode) o;
    return annotatedNode.equals(node.annotatedNode)
        && annotationClassNode.equals(node.annotationClassNode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(annotatedNode, annotationClassNode);
  }

  @Override
  public String toString() {
    return "annotated " + annotatedNode.toString();
  }
}
