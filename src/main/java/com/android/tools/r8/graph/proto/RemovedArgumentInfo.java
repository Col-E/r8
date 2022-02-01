// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

public class RemovedArgumentInfo extends ArgumentInfo {

  public static class Builder {

    private boolean checkNullOrZero;
    private SingleValue singleValue;
    private DexType type;

    public Builder setCheckNullOrZero(boolean checkNullOrZero) {
      this.checkNullOrZero = checkNullOrZero;
      return this;
    }

    public Builder setSingleValue(SingleValue singleValue) {
      this.singleValue = singleValue;
      return this;
    }

    public Builder setType(DexType type) {
      this.type = type;
      return this;
    }

    public RemovedArgumentInfo build() {
      assert type != null;
      return new RemovedArgumentInfo(checkNullOrZero, singleValue, type);
    }
  }

  private final boolean checkNullOrZero;
  private final SingleValue singleValue;
  private final DexType type;

  private RemovedArgumentInfo(boolean checkNullOrZero, SingleValue singleValue, DexType type) {
    this.checkNullOrZero = checkNullOrZero;
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

  public boolean isCheckNullOrZeroSet() {
    return checkNullOrZero;
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
  public RemovedArgumentInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    SingleValue rewrittenSingleValue =
        hasSingleValue() ? singleValue.rewrittenWithLens(appView, graphLens, codeLens) : null;
    DexType rewrittenType = graphLens.lookupType(type, codeLens);
    if (rewrittenSingleValue != singleValue || rewrittenType != type) {
      return new RemovedArgumentInfo(checkNullOrZero, rewrittenSingleValue, rewrittenType);
    }
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RemovedArgumentInfo other = (RemovedArgumentInfo) obj;
    return checkNullOrZero == other.checkNullOrZero
        && type == other.type
        && Objects.equals(singleValue, other.singleValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkNullOrZero, singleValue, type);
  }
}
