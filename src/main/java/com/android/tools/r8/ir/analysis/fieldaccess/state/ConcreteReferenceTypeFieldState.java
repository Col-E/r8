// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;

/** The information that we track for fields whose type is a reference type. */
public abstract class ConcreteReferenceTypeFieldState extends ConcreteFieldState {

  protected AbstractValue abstractValue;

  ConcreteReferenceTypeFieldState(AbstractValue abstractValue) {
    this.abstractValue = abstractValue;
  }

  @Override
  public AbstractValue getAbstractValue(
      AbstractValueFactory abstractValueFactory, ProgramField field) {
    return abstractValue;
  }

  @Override
  public boolean isReference() {
    return true;
  }

  @Override
  public ConcreteReferenceTypeFieldState asReference() {
    return this;
  }
}
