// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;

// Source code representing a simple call to CheckCast.
public final class InstanceOfSourceCode extends SyntheticCfCodeProvider {

  private final DexType instanceOfType;

  private InstanceOfSourceCode(AppView<?> appView, DexType holder, DexType instanceOfType) {
    super(appView, holder);
    this.instanceOfType = instanceOfType;
  }

  public static InstanceOfSourceCode create(
      AppView<?> appView, DexType holder, DexType checkCastType) {
    return new InstanceOfSourceCode(appView, holder, checkCastType);
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfInstanceOf(instanceOfType));
    instructions.add(new CfReturn(ValueType.INT));
    return standardCfCodeFromInstructions(instructions);
  }
}
