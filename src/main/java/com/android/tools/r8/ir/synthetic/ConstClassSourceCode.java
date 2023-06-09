// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;

// Source code representing a simple call to a const class.
public final class ConstClassSourceCode extends SyntheticCfCodeProvider {

  private final DexType constClassType;

  private ConstClassSourceCode(AppView<?> appView, DexType holder, DexType constClassType) {
    super(appView, holder);
    this.constClassType = constClassType;
  }

  public static ConstClassSourceCode create(
      AppView<?> appView, DexType holder, DexType checkCastType) {
    return new ConstClassSourceCode(appView, holder, checkCastType);
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfConstClass(constClassType));
    instructions.add(CfReturn.ARETURN);
    return standardCfCodeFromInstructions(instructions);
  }
}
