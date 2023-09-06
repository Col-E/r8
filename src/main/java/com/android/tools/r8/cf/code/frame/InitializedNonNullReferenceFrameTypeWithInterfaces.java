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

public class InitializedNonNullReferenceFrameTypeWithInterfaces
    extends InitializedNonNullReferenceFrameType {

  private final ReferenceTypeElement type;
  private DexType initializedTypeCache;

  InitializedNonNullReferenceFrameTypeWithInterfaces(ReferenceTypeElement type) {
    assert type != null;
    assert !type.isNullType();
    this.type = type;
  }

  @Override
  public boolean isInitializedNonNullReferenceTypeWithInterfaces() {
    return true;
  }

  @Override
  public InitializedNonNullReferenceFrameTypeWithInterfaces
      asInitializedNonNullReferenceTypeWithInterfaces() {
    return this;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    if (initializedTypeCache == null) {
      initializedTypeCache = type.toDexType(dexItemFactory);
    }
    return initializedTypeCache;
  }

  @Override
  public ReferenceTypeElement getInitializedTypeWithInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getInitializedTypeWithInterfaces();
  }

  public ReferenceTypeElement getInitializedTypeWithInterfaces() {
    return type;
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    throw new Unreachable(
        "Unexpected InitializedNonNullReferenceFrameTypeWithInterfaces in writer");
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
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InitializedNonNullReferenceFrameTypeWithInterfaces initializedType =
        (InitializedNonNullReferenceFrameTypeWithInterfaces) obj;
    return type.equals(initializedType.type);
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
