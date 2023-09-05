// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import java.util.Objects;

public class AssumeInfo {

  private static final AssumeInfo EMPTY =
      new AssumeInfo(DynamicType.unknown(), AbstractValue.unknown(), false);

  private final DynamicType assumeType;
  private final AbstractValue assumeValue;
  private final boolean isSideEffectFree;

  private AssumeInfo(DynamicType assumeType, AbstractValue assumeValue, boolean isSideEffectFree) {
    this.assumeType = assumeType;
    this.assumeValue = assumeValue;
    this.isSideEffectFree = isSideEffectFree;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static AssumeInfo create(
      DynamicType assumeType, AbstractValue assumeValue, boolean isSideEffectFree) {
    return assumeType.isUnknown() && assumeValue.isUnknown() && !isSideEffectFree
        ? empty()
        : new AssumeInfo(assumeType, assumeValue, isSideEffectFree);
  }

  public static AssumeInfo empty() {
    return EMPTY;
  }

  public DynamicType getAssumeType() {
    return assumeType;
  }

  public AbstractValue getAssumeValue() {
    return assumeValue;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isEmpty() {
    if (this == empty()) {
      return true;
    }
    assert !assumeType.isUnknown() || !assumeValue.isUnknown() || isSideEffectFree;
    return false;
  }

  public boolean isSideEffectFree() {
    return isSideEffectFree;
  }

  public AssumeInfo meet(AssumeInfo other) {
    DynamicType meetType = internalMeetType(assumeType, other.assumeType);
    AbstractValue meetValue = internalMeetValue(assumeValue, other.assumeValue);
    boolean meetIsSideEffectFree =
        internalMeetIsSideEffectFree(isSideEffectFree, other.isSideEffectFree);
    return AssumeInfo.create(meetType, meetValue, meetIsSideEffectFree);
  }

  private static DynamicType internalMeetType(DynamicType type, DynamicType other) {
    if (type.equals(other)) {
      return type;
    }
    if (type.isUnknown()) {
      return other;
    }
    if (other.isUnknown()) {
      return type;
    }
    return DynamicType.unknown();
  }

  private static AbstractValue internalMeetValue(AbstractValue value, AbstractValue other) {
    if (value.equals(other)) {
      return value;
    }
    if (value.isUnknown()) {
      return other;
    }
    if (other.isUnknown()) {
      return value;
    }
    return AbstractValue.bottom();
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean internalMeetIsSideEffectFree(
      boolean isSideEffectFree, boolean otherIsSideEffectFree) {
    return isSideEffectFree || otherIsSideEffectFree;
  }

  @SuppressWarnings("ReferenceEquality")
  public AssumeInfo rewrittenWithLens(AppView<?> appView, GraphLens graphLens) {
    // Verify that there is no need to rewrite the assumed type.
    assert assumeType.isNotNullType() || assumeType.isUnknown();
    // If the assumed value is a static field, then rewrite it.
    if (assumeValue.isSingleFieldValue()) {
      DexField field = assumeValue.asSingleFieldValue().getField();
      DexField rewrittenField = graphLens.getRenamedFieldSignature(field);
      if (rewrittenField != field) {
        SingleFieldValue rewrittenAssumeValue =
            appView
                .abstractValueFactory()
                .createSingleFieldValue(rewrittenField, ObjectState.empty());
        return create(assumeType, rewrittenAssumeValue, isSideEffectFree);
      }
    }
    return this;
  }

  public AssumeInfo withoutPrunedItems(PrunedItems prunedItems) {
    // Verify that there is no need to prune the assumed type.
    assert assumeType.isNotNullType() || assumeType.isUnknown();
    // If the assumed value is a static field, and the static field is removed, then prune the
    // assumed value.
    if (assumeValue.isSingleFieldValue()
        && prunedItems.isRemoved(assumeValue.asSingleFieldValue().getField())) {
      return create(assumeType, AbstractValue.unknown(), isSideEffectFree);
    }
    return this;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    AssumeInfo assumeInfo = (AssumeInfo) other;
    return assumeValue.equals(assumeInfo.assumeValue)
        && assumeType.equals(assumeInfo.assumeType)
        && isSideEffectFree == assumeInfo.isSideEffectFree;
  }

  @Override
  public int hashCode() {
    return Objects.hash(assumeValue, assumeType, isSideEffectFree);
  }

  public static class Builder {

    private DynamicType assumeType = DynamicType.unknown();
    private AbstractValue assumeValue = AbstractValue.unknown();
    private boolean isSideEffectFree = false;

    public Builder meet(AssumeInfo assumeInfo) {
      return meetAssumeType(assumeInfo.assumeType)
          .meetAssumeValue(assumeInfo.assumeValue)
          .meetIsSideEffectFree(assumeInfo.isSideEffectFree);
    }

    public Builder meetAssumeType(DynamicType assumeType) {
      this.assumeType = internalMeetType(this.assumeType, assumeType);
      return this;
    }

    public Builder meetAssumeValue(AbstractValue assumeValue) {
      this.assumeValue = internalMeetValue(this.assumeValue, assumeValue);
      return this;
    }

    public Builder meetIsSideEffectFree(boolean isSideEffectFree) {
      this.isSideEffectFree = internalMeetIsSideEffectFree(this.isSideEffectFree, isSideEffectFree);
      return this;
    }

    public Builder setIsSideEffectFree() {
      this.isSideEffectFree = true;
      return this;
    }

    public AssumeInfo build() {
      return create(assumeType, assumeValue, isSideEffectFree);
    }
  }
}
