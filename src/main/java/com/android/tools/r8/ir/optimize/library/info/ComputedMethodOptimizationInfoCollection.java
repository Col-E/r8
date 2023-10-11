// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.InvokeMethod;

public abstract class ComputedMethodOptimizationInfoCollection {

  AppView<?> appView;
  AbstractValueFactory abstractValueFactory;
  DexItemFactory dexItemFactory;

  public ComputedMethodOptimizationInfoCollection(AppView<?> appView) {
    this.appView = appView;
    this.abstractValueFactory = appView.abstractValueFactory();
    this.dexItemFactory = appView.dexItemFactory();
  }

  public abstract AbstractValue getAbstractReturnValueOrDefault(
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier);
}
