// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.position;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.stream.Collectors;

/** A {@link Position} denoting a method. */
@Keep
public class MethodPosition implements Position {

  private final MethodReference method;

  @Deprecated
  public MethodPosition(DexMethod method) {
    this(method.asMethodReference());
  }

  public MethodPosition(MethodReference method) {
    this.method = method;
  }

  /** The method */
  public MethodReference getMethod() {
    return method;
  }
  /** The unqualified name of the method. */
  public String getName() {
    return method.getMethodName();
  }

  /** The type descriptor of the method holder. */
  public String getHolder() {
    return method.getHolderClass().getDescriptor();
  }

  /** The type descriptor of the methods return type. */
  public String getReturnType() {
    return method.getReturnType().getDescriptor();
  }

  /** The type descriptors for the methods formal parameter types. */
  public List<String> getParameterTypes() {
    return method.getFormalTypes().stream()
        .map(TypeReference::getDescriptor)
        .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return method.toString();
  }

  @Override
  public String getDescription() {
    return toString();
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MethodPosition) {
      return method.equals(((MethodPosition) o).method);
    }
    return false;
  }
}
