// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class InitializedNonNullReferenceFrameTypeWithoutInterfaces
    extends InitializedNonNullReferenceFrameType {

  private final DexType type;

  InitializedNonNullReferenceFrameTypeWithoutInterfaces(DexType type) {
    assert type != null;
    assert type.isReferenceType();
    assert !type.isNullValueType();
    this.type = type;
  }

  @Override
  public boolean isInitializedNonNullReferenceTypeWithoutInterfaces() {
    return true;
  }

  @Override
  public InitializedNonNullReferenceFrameTypeWithoutInterfaces
      asInitializedNonNullReferenceTypeWithoutInterfaces() {
    return this;
  }

  @Override
  public final DexType getInitializedType(DexItemFactory dexItemFactory) {
    return getInitializedType();
  }

  public DexType getInitializedType() {
    return type;
  }

  @Override
  public ReferenceTypeElement getInitializedTypeWithInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return type.toTypeElement(appView).asReferenceType();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    DexType rewrittenType = graphLens.lookupType(type);
    assert rewrittenType != DexItemFactory.nullValueType;
    switch (rewrittenType.toShorty()) {
      case 'L':
        return namingLens.lookupInternalName(rewrittenType);
      case 'I':
        return Opcodes.INTEGER;
      case 'F':
        return Opcodes.FLOAT;
      case 'J':
        return Opcodes.LONG;
      case 'D':
        return Opcodes.DOUBLE;
      default:
        throw new Unreachable("Unexpected value type: " + rewrittenType);
    }
  }

  @Override
  public SingleFrameType join(
      AppView<? extends AppInfoWithClassHierarchy> appView, SingleFrameType frameType) {
    if (equals(frameType) || frameType.isNullType()) {
      return this;
    }
    if (frameType.isOneWord() || frameType.isPrimitive() || frameType.isUninitialized()) {
      return FrameType.oneWord();
    }
    assert frameType.isInitializedNonNullReferenceType();
    ReferenceTypeElement joinType =
        getInitializedTypeWithInterfaces(appView)
            .join(
                frameType
                    .asInitializedNonNullReferenceType()
                    .getInitializedTypeWithInterfaces(appView),
                appView);
    return FrameType.initializedNonNullReference(joinType);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InitializedNonNullReferenceFrameTypeWithoutInterfaces initializedType =
        (InitializedNonNullReferenceFrameTypeWithoutInterfaces) obj;
    return type == initializedType.type;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return "Initialized(" + type.toString() + ")";
  }
}
