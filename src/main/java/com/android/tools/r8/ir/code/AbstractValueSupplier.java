// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public interface AbstractValueSupplier {

  UnknownAbstractValueSupplier UNKNOWN = new UnknownAbstractValueSupplier();

  AbstractValue getAbstractValue(Value value);

  /**
   * Returns an {@link AbstractValueSupplier} that supplies a {@link UnknownAbstractValueSupplier}
   * in the recursive call to {@link Value#getAbstractValue}, so that a shallow value is computed.
   * This is to prevent that computing the abstract value can end up evaluating large arithmetic
   * expressions, which should ideally only be done during constant propagation.
   */
  static AbstractValueSupplier getShallow(AppView<?> appView, ProgramMethod context) {
    return value -> value.getAbstractValue(appView, context, unknown());
  }

  static UnknownAbstractValueSupplier unknown() {
    return UNKNOWN;
  }

  class UnknownAbstractValueSupplier implements AbstractValueSupplier {

    @Override
    public AbstractValue getAbstractValue(Value value) {
      return AbstractValue.unknown();
    }
  }
}
