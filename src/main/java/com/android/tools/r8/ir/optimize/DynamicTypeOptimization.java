// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.List;

public class DynamicTypeOptimization {

  private final AppView<AppInfoWithLiveness> appView;

  public DynamicTypeOptimization(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  /**
   * Computes the dynamic return type of the given method.
   *
   * <p>If the method has no normal exits, then null is returned.
   */
  public DynamicType computeDynamicReturnType(ProgramMethod method, IRCode code) {
    assert method.getReturnType().isReferenceType();
    List<DynamicType> returnedTypes = new ArrayList<>();
    for (BasicBlock block : code.getBlocks()) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        Value returnValue = exitInstruction.asReturn().returnValue();
        returnedTypes.add(returnValue.getDynamicType(appView));
      }
    }
    return DynamicType.join(appView, returnedTypes);
  }
}
