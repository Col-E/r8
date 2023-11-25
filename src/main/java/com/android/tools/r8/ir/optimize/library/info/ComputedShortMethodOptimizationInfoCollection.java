// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.InvokeMethod;

public class ComputedShortMethodOptimizationInfoCollection
    extends ComputedMethodOptimizationInfoCollection {

  public ComputedShortMethodOptimizationInfoCollection(AppView<?> appView) {
    super(appView);
  }

  @Override
  public AbstractValue getAbstractReturnValueOrDefault(
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier) {
    if (singleTarget.getReference().isIdenticalTo(dexItemFactory.shortMembers.valueOf)) {
      AbstractValue operandValue =
          invoke.getFirstOperand().getAbstractValue(appView, context, abstractValueSupplier);
      if (operandValue.isSingleNumberValue()) {
        return abstractValueFactory.createBoxedShort(
            operandValue.asSingleNumberValue().getIntValue());
      }
    }
    return AbstractValue.unknown();
  }
}
