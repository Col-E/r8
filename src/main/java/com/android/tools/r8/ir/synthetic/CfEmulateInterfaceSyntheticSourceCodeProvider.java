// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class CfEmulateInterfaceSyntheticSourceCodeProvider extends CfSyntheticSourceCodeProvider {

  private final DexType interfaceType;
  private final DexMethod companionMethod;
  private final DexMethod libraryMethod;

  public CfEmulateInterfaceSyntheticSourceCodeProvider(
      DexType interfaceType,
      DexMethod companionMethod,
      DexEncodedMethod method,
      DexMethod libraryMethod,
      DexMethod originalMethod,
      AppView<?> appView) {
    super(method, originalMethod, appView);
    this.interfaceType = interfaceType;
    this.companionMethod = companionMethod;
    this.libraryMethod = libraryMethod;
  }

  @Override
  protected CfCode generateCfCode(Position callerPosition) {
    List<CfInstruction> instructions = new ArrayList<>();
    CfLabel companionLabel = new CfLabel();
    instructions.add(new CfLoad(ValueType.fromDexType(interfaceType), 0));
    instructions.add(new CfInstanceOf(libraryMethod.holder));
    instructions.add(new CfIf(If.Type.NE, ValueType.INT, companionLabel));

    // Branch with library call.
    instructions.add(new CfLoad(ValueType.fromDexType(interfaceType), 0));
    instructions.add(new CfCheckCast(libraryMethod.holder));
    loadExtraParameters(instructions);
    instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, libraryMethod, true));
    addReturn(instructions);

    // Branch with companion call.
    instructions.add(companionLabel);
    instructions.add(new CfLoad(ValueType.fromDexType(interfaceType), 0));
    loadExtraParameters(instructions);
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, companionMethod, false));
    addReturn(instructions);
    return standardCfCodeFromInstructions(instructions);
  }

  private void loadExtraParameters(List<CfInstruction> instructions) {
    int index = 1;
    for (DexType type : libraryMethod.proto.parameters.values) {
      instructions.add(new CfLoad(ValueType.fromDexType(type), index++));
    }
  }

  private void addReturn(List<CfInstruction> instructions) {
    if (libraryMethod.proto.returnType == appView.dexItemFactory().voidType) {
      instructions.add(new CfReturnVoid());
    } else {
      instructions.add(new CfReturn(ValueType.fromDexType(libraryMethod.proto.returnType)));
    }
  }
}
