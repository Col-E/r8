// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.position;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.stream.Collectors;

/** A {@link Position} denoting a method. */
@KeepForApi
public class MethodPosition implements Position {

  private final MethodReference method;
  private final Position textPosition;

  @Deprecated
  public MethodPosition(DexMethod method) {
    this(method.asMethodReference());
  }

  @Deprecated
  public MethodPosition(MethodReference method) {
    this(method, Position.UNKNOWN);
  }

  private MethodPosition(MethodReference method, Position textPosition) {
    this.method = method;
    this.textPosition = textPosition;
  }

  public static MethodPosition create(ProgramMethod method) {
    return create(method.getDefinition());
  }

  public static MethodPosition create(DexEncodedMethod method) {
    Position position = UNKNOWN;
    if (method.hasCode() && method.getCode().isCfCode()) {
      position = method.getCode().asCfCode().getDiagnosticPosition();
    }
    return create(method.getReference().asMethodReference(), position);
  }

  public static MethodPosition create(MethodReference method) {
    return new MethodPosition(method, Position.UNKNOWN);
  }

  public static MethodPosition create(MethodReference method, Position position) {
    return new MethodPosition(method, position);
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

  public Position getTextPosition() {
    return textPosition;
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
