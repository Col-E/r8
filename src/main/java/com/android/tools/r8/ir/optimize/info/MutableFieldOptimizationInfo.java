// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import static java.util.Collections.emptySet;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

/**
 * Optimization info for fields.
 *
 * <p>NOTE: Unlike the optimization info for methods, the field optimization info is currently being
 * updated directly, meaning that updates may become visible to concurrently processed methods in
 * the {@link com.android.tools.r8.ir.conversion.IRConverter}.
 */
public class MutableFieldOptimizationInfo extends FieldOptimizationInfo
    implements MutableOptimizationInfo {

  private static final int FLAGS_CANNOT_BE_KEPT = 1 << 0;
  private static final int FLAGS_IS_DEAD = 1 << 1;
  private static final int FLAGS_VALUE_HAS_BEEN_PROPAGATED = 1 << 2;

  private AbstractValue abstractValue = UnknownValue.getInstance();
  private int flags;
  private int readBits = 0;
  private DynamicType dynamicType = DynamicType.unknown();

  public MutableFieldOptimizationInfo fixupClassTypeReferences(
      AppView<AppInfoWithLiveness> appView, GraphLens lens) {
    return fixupClassTypeReferences(appView, lens, emptySet());
  }

  public MutableFieldOptimizationInfo fixupClassTypeReferences(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, Set<DexType> prunedTypes) {
    dynamicType = dynamicType.rewrittenWithLens(appView, lens, prunedTypes);
    return this;
  }

  public MutableFieldOptimizationInfo mutableCopy() {
    MutableFieldOptimizationInfo copy = new MutableFieldOptimizationInfo();
    copy.abstractValue = abstractValue;
    copy.flags = flags;
    copy.readBits = readBits;
    copy.dynamicType = dynamicType;
    return copy;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  MutableFieldOptimizationInfo setAbstractValue(
      AbstractValue abstractValue, DexEncodedField field) {
    assert !abstractValue.isNull() || field.getType().isReferenceType();
    return setAbstractValue(abstractValue);
  }

  private MutableFieldOptimizationInfo setAbstractValue(AbstractValue abstractValue) {
    assert getAbstractValue().isUnknown() || abstractValue.isNonTrivial();
    this.abstractValue = abstractValue;
    return this;
  }

  public MutableFieldOptimizationInfo fixupAbstractValue(
      AppView<AppInfoWithLiveness> appView,
      DexEncodedField field,
      GraphLens lens,
      GraphLens codeLens) {
    return setAbstractValue(
        abstractValue.rewrittenWithLens(appView, field.getType(), lens, codeLens), field);
  }

  @Override
  public int getReadBits() {
    return readBits;
  }

  void joinReadBits(int readBits) {
    this.readBits |= readBits;
  }

  @Override
  public boolean cannotBeKept() {
    return (flags & FLAGS_CANNOT_BE_KEPT) != 0;
  }

  void markCannotBeKept() {
    flags |= FLAGS_CANNOT_BE_KEPT;
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicType;
  }

  void setDynamicType(DynamicType dynamicType) {
    this.dynamicType = dynamicType;
  }

  @Override
  public boolean isDead() {
    return (flags & FLAGS_IS_DEAD) != 0;
  }

  void markAsDead() {
    flags |= FLAGS_IS_DEAD;
  }

  @Override
  public boolean valueHasBeenPropagated() {
    return (flags & FLAGS_VALUE_HAS_BEEN_PROPAGATED) != 0;
  }

  void markAsPropagated() {
    flags |= FLAGS_VALUE_HAS_BEEN_PROPAGATED;
  }

  @Override
  public boolean isMutableOptimizationInfo() {
    return true;
  }

  @Override
  public MutableFieldOptimizationInfo toMutableOptimizationInfo() {
    return this;
  }

  @Override
  public MutableFieldOptimizationInfo asMutableFieldOptimizationInfo() {
    return this;
  }
}
