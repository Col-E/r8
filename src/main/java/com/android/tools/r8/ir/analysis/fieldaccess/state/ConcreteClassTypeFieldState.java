// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** The information that we track for fields whose type is a class type. */
public class ConcreteClassTypeFieldState extends ConcreteReferenceTypeFieldState {

  private DynamicType dynamicType;

  ConcreteClassTypeFieldState(AbstractValue abstractValue, DynamicType dynamicType) {
    super(abstractValue);
    this.dynamicType = dynamicType;
  }

  public static FieldState create(AbstractValue abstractValue, DynamicType dynamicType) {
    return abstractValue.isUnknown() && dynamicType.isUnknown()
        ? FieldState.unknown()
        : new ConcreteClassTypeFieldState(abstractValue, dynamicType);
  }

  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public ConcreteClassTypeFieldState asClass() {
    return this;
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      AbstractValue abstractValue,
      DynamicType dynamicType,
      ProgramField field) {
    assert field.getType().isClassType();
    this.abstractValue =
        appView.getAbstractValueFieldJoiner().join(this.abstractValue, abstractValue, field);
    this.dynamicType =
        WideningUtils.widenDynamicNonReceiverType(
            appView, this.dynamicType.join(appView, dynamicType), field.getType());
    return isEffectivelyUnknown() ? unknown() : this;
  }

  private boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown() && dynamicType.isUnknown();
  }
}
