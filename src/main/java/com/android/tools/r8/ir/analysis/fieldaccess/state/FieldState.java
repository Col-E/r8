// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;

/** An abstraction of the runtime values that may flow into each field. */
public abstract class FieldState {

  public static BottomFieldState bottom() {
    return BottomFieldState.getInstance();
  }

  public static UnknownFieldState unknown() {
    return UnknownFieldState.getInstance();
  }

  public abstract AbstractValue getAbstractValue(
      AbstractValueFactory abstractValueFactory, ProgramField field);

  public boolean isArray() {
    return false;
  }

  public ConcreteArrayTypeFieldState asArray() {
    return null;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isClass() {
    return false;
  }

  public ConcreteClassTypeFieldState asClass() {
    return null;
  }

  public boolean isConcrete() {
    return false;
  }

  public ConcreteFieldState asConcrete() {
    return null;
  }

  public boolean isPrimitive() {
    return false;
  }

  public ConcretePrimitiveTypeFieldState asPrimitive() {
    return null;
  }

  public boolean isReference() {
    return false;
  }

  public ConcreteReferenceTypeFieldState asReference() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }
}
