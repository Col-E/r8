// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;
import java.util.function.Consumer;

public class RewrittenTypeInfo extends ArgumentInfo {

  private final DexType castType;
  private final DexType oldType;
  private final DexType newType;
  private final SingleValue singleValue;

  public static Builder builder() {
    return new Builder();
  }

  private RewrittenTypeInfo(
      DexType oldType, DexType newType, DexType castType, SingleValue singleValue) {
    this.castType = castType;
    this.oldType = oldType;
    this.newType = newType;
    this.singleValue = singleValue;
  }

  public RewrittenTypeInfo combine(RewrittenPrototypeDescription other) {
    return other.hasRewrittenReturnInfo() ? combine(other.getRewrittenReturnInfo()) : this;
  }

  public DexType getCastType() {
    return castType;
  }

  public DexType getNewType() {
    return newType;
  }

  public DexType getOldType() {
    return oldType;
  }

  public SingleValue getSingleValue() {
    return singleValue;
  }

  boolean hasBeenChangedToReturnVoid() {
    return newType.isVoidType();
  }

  public boolean hasCastType() {
    return castType != null;
  }

  public boolean hasSingleValue() {
    return singleValue != null;
  }

  @Override
  public boolean isRewrittenTypeInfo() {
    return true;
  }

  @Override
  public RewrittenTypeInfo asRewrittenTypeInfo() {
    return this;
  }

  @Override
  public ArgumentInfo combine(ArgumentInfo info) {
    if (info.isRemovedArgumentInfo()) {
      return info;
    }
    assert info.isRewrittenTypeInfo();
    return combine(info.asRewrittenTypeInfo());
  }

  @SuppressWarnings("ReferenceEquality")
  public RewrittenTypeInfo combine(RewrittenTypeInfo other) {
    assert !getNewType().isVoidType();
    assert getNewType() == other.getOldType();
    return new RewrittenTypeInfo(
        getOldType(), other.getNewType(), getCastType(), other.getSingleValue());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public RewrittenTypeInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    DexType rewrittenCastType = castType != null ? graphLens.lookupType(castType, codeLens) : null;
    DexType rewrittenNewType = graphLens.lookupType(newType, codeLens);
    SingleValue rewrittenSingleValue =
        hasSingleValue() ? getSingleValue().rewrittenWithLens(appView, graphLens, codeLens) : null;
    if (rewrittenCastType != castType
        || rewrittenNewType != newType
        || rewrittenSingleValue != singleValue) {
      // The old type is intentionally not rewritten.
      return new RewrittenTypeInfo(
          oldType, rewrittenNewType, rewrittenCastType, rewrittenSingleValue);
    }
    return this;
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RewrittenTypeInfo other = (RewrittenTypeInfo) obj;
    return oldType == other.oldType
        && newType == other.newType
        && Objects.equals(singleValue, other.singleValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldType, newType, singleValue);
  }

  public static class Builder {

    private DexType castType;
    private DexType oldType;
    private DexType newType;
    private SingleValue singleValue;

    public Builder applyIf(boolean condition, Consumer<Builder> consumer) {
      if (condition) {
        consumer.accept(this);
      }
      return this;
    }

    public Builder setCastType(DexType castType) {
      this.castType = castType;
      return this;
    }

    public Builder setOldType(DexType oldType) {
      this.oldType = oldType;
      return this;
    }

    public Builder setNewType(DexType newType) {
      this.newType = newType;
      return this;
    }

    public Builder setSingleValue(SingleValue singleValue) {
      this.singleValue = singleValue;
      return this;
    }

    public RewrittenTypeInfo build() {
      return new RewrittenTypeInfo(oldType, newType, castType, singleValue);
    }
  }
}
