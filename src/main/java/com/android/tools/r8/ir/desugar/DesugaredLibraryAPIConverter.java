// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.utils.StringDiagnostic;

public class DesugaredLibraryAPIConverter {

  AppView<?> appView;

  public DesugaredLibraryAPIConverter(AppView<?> appView) {
    this.appView = appView;
  }

  public void desugar(IRCode code) {
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }
      InvokeMethod invokeMethod = instruction.asInvokeMethod();
      DexMethod invokedMethod = invokeMethod.getInvokedMethod();
      if (appView.rewritePrefix.hasRewrittenType(invokedMethod.holder)) {
        continue;
      }
      for (DexType argType : invokedMethod.proto.parameters.values) {
        if (appView.rewritePrefix.hasRewrittenType(argType)) {
          // In this case, the method has not been rewritten and is not on a rewritten class.
          // This invoke will (likely) not work at runtime.
          appView
              .options()
              .reporter
              .warning(
                  new StringDiagnostic(
                      "Invoke to "
                          + invokedMethod.holder
                          + "#"
                          + invokedMethod.name
                          + " may not work correctly at runtime (Parameter "
                          + appView.rewritePrefix.rewrittenType(argType)
                          + " is a desugared type)."));
          continue;
        }
      }
    }
  }
}
