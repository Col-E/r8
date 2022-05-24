// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic.apiconverter;

import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class HashCodeCfCodeProvider extends SyntheticCfCodeProvider {

  private final DexField wrapperField;

  public HashCodeCfCodeProvider(AppView<?> appView, DexType holder, DexField wrapperField) {
    super(appView, holder);
    this.wrapperField = wrapperField;
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfInstanceFieldRead(wrapperField));
    instructions.add(
        new CfInvoke(
            Opcodes.INVOKEVIRTUAL, appView.dexItemFactory().objectMembers.hashCode, false));
    instructions.add(new CfReturn(ValueType.INT));
    return standardCfCodeFromInstructions(instructions);
  }
}
