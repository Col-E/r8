// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * The information that we track for fields with an array type.
 *
 * <p>Since we don't gain much from tracking the dynamic types of arrays, this is only tracking the
 * abstract value.
 */
public class ConcreteArrayTypeFieldState extends ConcreteReferenceTypeFieldState {

  ConcreteArrayTypeFieldState(AbstractValue abstractValue) {
    super(abstractValue);
  }

  public static FieldState create(AbstractValue abstractValue) {
    return abstractValue.isUnknown()
        ? FieldState.unknown()
        : new ConcreteArrayTypeFieldState(abstractValue);
  }

  @Override
  public boolean isArray() {
    return true;
  }

  @Override
  public ConcreteArrayTypeFieldState asArray() {
    return this;
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue abstractValue) {
    this.abstractValue =
        appView.getAbstractValueFieldJoiner().join(this.abstractValue, abstractValue, field);
    return isEffectivelyUnknown() ? unknown() : this;
  }

  private boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }
}
