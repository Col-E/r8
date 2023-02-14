// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizationsEventConsumer;

public interface ConstantDynamicDesugaringEventConsumer
    extends UtilityMethodsForCodeOptimizationsEventConsumer {

  void acceptConstantDynamicClass(ConstantDynamicClass constantDynamicClass, ProgramMethod context);

  void acceptConstantDynamicRewrittenBootstrapMethod(
      ProgramMethod bootstrapMethod, DexMethod oldSignature);
}
