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

public class RemovedArgumentInfo extends ArgumentInfo {

  abstract static class BuilderBase<B extends BuilderBase<B>> {

    SingleValue singleValue;
    DexType type;

    public B setSingleValue(SingleValue singleValue) {
      this.singleValue = singleValue;
      return self();
    }

    public B setType(DexType type) {
      this.type = type;
      return self();
    }

    abstract B self();
  }

  public static class Builder extends BuilderBase<Builder> {

    public RemovedArgumentInfo build() {
      return new RemovedArgumentInfo(singleValue, type);
    }

    @Override
    Builder self() {
      return this;
    }
  }

  private final SingleValue singleValue;
  private final DexType type;

  RemovedArgumentInfo(SingleValue singleValue, DexType type) {
    assert type != null;
    this.singleValue = singleValue;
    this.type = type;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean hasSingleValue() {
    return singleValue != null;
  }

  public SingleValue getSingleValue() {
    return singleValue;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public boolean isRemovedArgumentInfo() {
    return true;
  }

  @Override
  public RemovedArgumentInfo asRemovedArgumentInfo() {
    return this;
  }

  @Override
  public ArgumentInfo combine(ArgumentInfo info) {
    assert false : "Once the argument is removed one cannot modify it any further.";
    return this;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public RemovedArgumentInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    DexType rewrittenType = graphLens.lookupType(type, codeLens);
    SingleValue rewrittenSingleValue =
        hasSingleValue()
            ? singleValue.rewrittenWithLens(appView, rewrittenType, graphLens, codeLens)
            : null;
    if (rewrittenSingleValue != singleValue || rewrittenType != type) {
      return new RemovedArgumentInfo(rewrittenSingleValue, rewrittenType);
    }
    return this;
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RemovedArgumentInfo other = (RemovedArgumentInfo) obj;
    return type == other.type && Objects.equals(singleValue, other.singleValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(singleValue, type);
  }
}
