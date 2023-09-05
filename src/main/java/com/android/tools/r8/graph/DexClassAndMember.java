// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.optimize.info.MemberOptimizationInfo;
import com.android.tools.r8.origin.Origin;

public abstract class DexClassAndMember<D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
    implements Definition {

  private final DexClass holder;
  private final D definition;

  @SuppressWarnings("ReferenceEquality")
  public DexClassAndMember(DexClass holder, D definition) {
    assert holder != null;
    assert definition != null;
    assert holder.type == definition.getHolderType();
    this.holder = holder;
    this.definition = definition;
  }

  public final DexAnnotationSet getAnnotations() {
    return definition.annotations();
  }

  @Override
  public DexClass getContextClass() {
    return getHolder();
  }

  @Override
  public DexType getContextType() {
    return getHolderType();
  }

  public DexClass getHolder() {
    return holder;
  }

  public DexType getHolderType() {
    return holder.type;
  }

  @Override
  public D getDefinition() {
    return definition;
  }

  public DexString getName() {
    return getReference().getName();
  }

  @Override
  public R getReference() {
    return definition.getReference();
  }

  public MemberOptimizationInfo<?> getOptimizationInfo() {
    return getDefinition().getOptimizationInfo();
  }

  @Override
  public Origin getOrigin() {
    return holder.origin;
  }

  public String toSourceString() {
    return getReference().toSourceString();
  }

  @Override
  public String toString() {
    return toSourceString();
  }

  @Override
  public boolean equals(Object object) {
    throw new Unreachable("Unsupported attempt at comparing Class and DexClassAndMember");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hash code of DexClassAndMember");
  }
}
