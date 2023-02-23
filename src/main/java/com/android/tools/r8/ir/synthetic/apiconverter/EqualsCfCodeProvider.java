// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic.apiconverter;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class EqualsCfCodeProvider extends SyntheticCfCodeProvider {

  private final DexField wrapperField;

  public EqualsCfCodeProvider(AppView<?> appView, DexType holder, DexField wrapperField) {
    super(appView, holder);
    this.wrapperField = wrapperField;
  }

  @Override
  public CfCode generateCfCode() {
    // return wrapperField.equals(
    //     other instanceof WrapperType ? ((WrapperType) other).wrapperField : other);
    DexType wrapperType = wrapperField.getHolderType();
    FrameType[] locals = {
      FrameType.initialized(wrapperType), FrameType.initialized(appView.dexItemFactory().objectType)
    };
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    // this.wrapperField
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfInstanceFieldRead(wrapperField));

    // other instanceof WrapperType
    instructions.add(new CfLoad(ValueType.OBJECT, 1));
    instructions.add(new CfInstanceOf(wrapperType));
    instructions.add(new CfIf(IfType.EQ, ValueType.INT, label1));

    // ((WrapperType) other).wrapperField
    instructions.add(new CfLoad(ValueType.OBJECT, 1));
    instructions.add(new CfCheckCast(wrapperType));
    instructions.add(new CfInstanceFieldRead(wrapperField));
    instructions.add(new CfGoto(label2));
    instructions.add(label1);
    instructions.add(
        new CfFrame(
            new Int2ObjectAVLTreeMap<>(new int[] {0, 1}, locals),
            new ArrayDeque<>(Arrays.asList(FrameType.initialized(wrapperField.type)))));

    // other
    instructions.add(new CfLoad(ValueType.OBJECT, 1));
    instructions.add(label2);
    instructions.add(
        new CfFrame(
            new Int2ObjectAVLTreeMap<>(new int[] {0, 1}, locals),
            new ArrayDeque<>(
                Arrays.asList(
                    FrameType.initialized(wrapperField.type),
                    FrameType.initialized(appView.dexItemFactory().objectType)))));

    // equals.
    instructions.add(
        new CfInvoke(Opcodes.INVOKEVIRTUAL, appView.dexItemFactory().objectMembers.equals, false));
    instructions.add(new CfReturn(ValueType.INT));
    return standardCfCodeFromInstructions(instructions);
  }
}
