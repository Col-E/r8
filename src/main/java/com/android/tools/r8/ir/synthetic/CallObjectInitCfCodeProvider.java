// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class CallObjectInitCfCodeProvider extends SyntheticCfCodeProvider {

  public CallObjectInitCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  @Override
  public CfCode generateCfCode() {
    DexItemFactory factory = appView.dexItemFactory();
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, factory.objectMembers.constructor, false));
    instructions.add(CfReturnVoid.INSTANCE);
    return standardCfCodeFromInstructions(instructions);
  }
}
