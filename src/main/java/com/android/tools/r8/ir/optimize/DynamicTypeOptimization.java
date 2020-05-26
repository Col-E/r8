// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
  public TypeElement computeDynamicReturnType(DexEncodedMethod method, IRCode code) {
    assert method.method.proto.returnType.isReferenceType();
    List<TypeElement> returnedTypes = new ArrayList<>();
    for (BasicBlock block : code.blocks) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        Value returnValue = exitInstruction.asReturn().returnValue();
        returnedTypes.add(returnValue.getDynamicUpperBoundType(appView));
      }
    }
    return returnedTypes.isEmpty() ? null : TypeElement.join(returnedTypes, appView);
  }

  public ClassTypeElement computeDynamicLowerBoundType(DexEncodedMethod method, IRCode code) {
    assert method.method.proto.returnType.isReferenceType();
    ClassTypeElement result = null;
    for (BasicBlock block : code.blocks) {
      JumpInstruction exitInstruction = block.exit();
      if (exitInstruction.isReturn()) {
        Value returnValue = exitInstruction.asReturn().returnValue();
        ClassTypeElement dynamicLowerBoundType = returnValue.getDynamicLowerBoundType(appView);
        if (dynamicLowerBoundType == null) {
          return null;
        }
        if (result == null) {
          result = dynamicLowerBoundType;
        } else if (dynamicLowerBoundType.equalUpToNullability(result)) {
          if (dynamicLowerBoundType.nullability() != result.nullability()) {
            result = dynamicLowerBoundType.join(result, appView).asClassType();
          }
        } else {
          return null;
        }
      }
    }
    return result;
  }
}
