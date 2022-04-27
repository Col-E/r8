// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic.apiconverter;

import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class WrapperConstructorCfCodeProvider extends SyntheticCfCodeProvider {

  private final DexField wrapperField;
  private final DexType superType;

  public WrapperConstructorCfCodeProvider(
      AppView<?> appView, DexField wrapperField, DexType superType) {
    super(appView, wrapperField.holder);
    this.wrapperField = wrapperField;
    this.superType = superType;
  }

  @Override
  public CfCode generateCfCode() {
    DexItemFactory factory = appView.dexItemFactory();
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
    instructions.add(
        new CfInvoke(
            Opcodes.INVOKESPECIAL,
            factory.createMethod(
                superType, factory.createProto(factory.voidType), factory.constructorMethodName),
            false));
    instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
    instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.type), 1));
    instructions.add(new CfInstanceFieldWrite(wrapperField));
    instructions.add(new CfReturnVoid());
    return standardCfCodeFromInstructions(instructions);
  }
}
