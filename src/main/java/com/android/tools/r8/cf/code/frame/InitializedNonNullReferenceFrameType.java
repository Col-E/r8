// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class InitializedNonNullReferenceFrameType extends BaseFrameType
    implements InitializedReferenceFrameType {

  private final DexType type;

  InitializedNonNullReferenceFrameType(DexType type) {
    assert type != null;
    assert type.isReferenceType();
    assert !type.isNullValueType();
    this.type = type;
  }

  @Override
  public boolean isPrecise() {
    return true;
  }

  @Override
  public PreciseFrameType asPrecise() {
    return this;
  }

  @Override
  public boolean isInitializedReferenceType() {
    return true;
  }

  @Override
  public InitializedNonNullReferenceFrameType asInitializedReferenceType() {
    return this;
  }

  @Override
  public boolean isInitializedNonNullReferenceType() {
    return true;
  }

  @Override
  public InitializedNonNullReferenceFrameType asInitializedNonNullReferenceType() {
    return this;
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
    DexType otherType = frameType.asInitializedNonNullReferenceType().getInitializedType();
    DexType joinType =
        DexTypeUtils.toDexType(
            appView, type.toTypeElement(appView).join(otherType.toTypeElement(appView), appView));
    return FrameType.initializedNonNullReference(joinType);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InitializedNonNullReferenceFrameType initializedType =
        (InitializedNonNullReferenceFrameType) obj;
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

  @Override
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
  public SingleFrameType asSingle() {
    return this;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public DexType getInitializedType() {
    return type;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    return getInitializedType();
  }

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public DexType getObjectType(DexType context) {
    return type;
  }
}
