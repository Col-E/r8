// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

public class RemovedReceiverInfo extends RemovedArgumentInfo {

  RemovedReceiverInfo(SingleValue singleValue, DexType type) {
    super(singleValue, type);
  }

  @Override
  public boolean isRemovedReceiverInfo() {
    return true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public RemovedReceiverInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    SingleValue rewrittenSingleValue =
        hasSingleValue() ? getSingleValue().rewrittenWithLens(appView, graphLens, codeLens) : null;
    DexType rewrittenType = graphLens.lookupType(getType(), codeLens);
    if (rewrittenSingleValue != getSingleValue() || rewrittenType != getType()) {
      return new RemovedReceiverInfo(rewrittenSingleValue, rewrittenType);
    }
    return this;
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RemovedReceiverInfo other = (RemovedReceiverInfo) obj;
    return getType() == other.getType() && Objects.equals(getSingleValue(), other.getSingleValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getSingleValue(), getType());
  }

  public static class Builder extends BuilderBase<Builder> {

    public static Builder create() {
      return new Builder();
    }

    public RemovedReceiverInfo build() {
      return new RemovedReceiverInfo(singleValue, type);
    }

    @Override
    Builder self() {
      return this;
    }
  }
}
