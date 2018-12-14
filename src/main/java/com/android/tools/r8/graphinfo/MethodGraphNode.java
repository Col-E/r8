// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexMethod;

@Keep
public final class MethodGraphNode extends GraphNode {

  private final DexMethod method;

  public MethodGraphNode(DexMethod method) {
    assert method != null;
    this.method = method;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof MethodGraphNode && ((MethodGraphNode) o).method == method);
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }

  /**
   * Get the class descriptor for the method holder as defined by the JVM specification.
   *
   * <p>For the method {@code void a.b.C.foo(x.y.Z arg)}, this would be {@code La/b/C;}.
   */
  public String getHolderDescriptor() {
    return method.holder.toDescriptorString();
  }

  /**
   * Get the method descriptor as defined by the JVM specification.
   *
   * <p>For the method {@code void a.b.C.foo(x.y.Z arg)}, this would be {@code (Lx/y/Z;)V}.
   */
  public String getMethodDescriptor() {
    return method.proto.toDescriptorString();
  }

  /**
   * Get the (unqualified) method name.
   *
   * <p>For the method {@code void a.b.C.foo(x.y.Z arg)} this would be {@code foo}.
   */
  public String getMethodName() {
    return method.name.toString();
  }

  /**
   * Get a unique identity string determining this method node.
   *
   * <p>The identity string follows the CF encoding of a method reference:
   * {@code <holder-descriptor><method-name><method-descriptor>}, e.g., for
   * {@code void a.b.C.foo(x.y.Z arg)} this will be {@code La/b/C;foo(Lx/y/Z;)V}.
   */
  @Override
  public String identity() {
    return getHolderDescriptor() + getMethodName() + getMethodDescriptor();
  }
}
