// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.Opcodes;

public class NullFrameType extends SingletonFrameType implements InitializedReferenceFrameType {

  static final NullFrameType SINGLETON = new NullFrameType();

  private NullFrameType() {}

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean isInitializedReferenceType() {
    return true;
  }

  @Override
  public NullFrameType asInitializedReferenceType() {
    return this;
  }

  @Override
  public boolean isNullType() {
    return true;
  }

  @Override
  public NullFrameType asNullType() {
    return this;
  }

  @Override
  public boolean isObject() {
    return true;
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
  public SingleFrameType asSingle() {
    return this;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    return getInitializedType();
  }

  public DexType getInitializedType() {
    return DexItemFactory.nullValueType;
  }

  @Override
  public DexType getObjectType(DexItemFactory dexItemFactory, DexType context) {
    return getInitializedType();
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return Opcodes.NULL;
  }

  @Override
  public SingleFrameType join(
      AppView<? extends AppInfoWithClassHierarchy> appView, SingleFrameType frameType) {
    if (this == frameType) {
      return this;
    }
    if (frameType.isOneWord() || frameType.isPrimitive() || frameType.isUninitialized()) {
      return FrameType.oneWord();
    }
    assert frameType.isInitializedNonNullReferenceType();
    return frameType;
  }

  @Override
  public String toString() {
    return "null";
  }
}
