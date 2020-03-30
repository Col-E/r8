// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class EnumUnboxingCfCodeProvider extends SyntheticCfCodeProvider {

  EnumUnboxingCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  public static class EnumUnboxingValueOfCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private DexType enumType;
    private EnumValueInfoMap map;

    public EnumUnboxingValueOfCfCodeProvider(
        AppView<?> appView, DexType holder, DexType enumType, EnumValueInfoMap map) {
      super(appView, holder);
      this.enumType = enumType;
      this.map = map;
    }

    @Override
    public CfCode generateCfCode() {
      // Generated static method, for class com.x.MyEnum {A,B} would look like:
      // int UtilityClass#com.x.MyEnum_valueOf(String s) {
      // 	if (s == null) { throw npe("Name is null"); }
      // 	if (s.equals("A")) { return 1;}
      // 	if (s.equals("B")) { return 2;}
      //  throw new IllegalArgumentException(
      //            "No enum constant com.x.MyEnum." + s);
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      // if (s == null) { throw npe("Name is null"); }
      CfLabel nullDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(factory.stringType), 0));
      instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
      instructions.add(new CfNew(factory.npeType));
      instructions.add(new CfStackInstruction(Opcode.Dup));
      instructions.add(new CfConstString(appView.dexItemFactory().createString("Name is null")));
      instructions.add(
          new CfInvoke(Opcodes.INVOKESPECIAL, factory.npeMethods.initWithMessage, false));
      instructions.add(new CfThrow());
      instructions.add(nullDest);

      // if (s.equals("A")) { return 1;}
      // if (s.equals("B")) { return 2;}
      map.forEach(
          (field, enumValueInfo) -> {
            CfLabel dest = new CfLabel();
            instructions.add(new CfLoad(ValueType.fromDexType(factory.stringType), 0));
            instructions.add(new CfConstString(field.name));
            instructions.add(
                new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMethods.equals, false));
            instructions.add(new CfIf(If.Type.EQ, ValueType.INT, dest));
            instructions.add(new CfConstNumber(enumValueInfo.convertToInt(), ValueType.INT));
            instructions.add(new CfReturn(ValueType.INT));
            instructions.add(dest);
          });

      // throw new IllegalArgumentException("No enum constant com.x.MyEnum." + s);
      instructions.add(new CfNew(factory.illegalArgumentExceptionType));
      instructions.add(new CfStackInstruction(Opcode.Dup));
      instructions.add(
          new CfConstString(
              appView
                  .dexItemFactory()
                  .createString(
                      "No enum constant " + enumType.toSourceString().replace('$', '.') + ".")));
      instructions.add(new CfLoad(ValueType.fromDexType(factory.stringType), 0));
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMethods.concat, false));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.illegalArgumentExceptionMethods.initWithMessage,
              false));
      instructions.add(new CfThrow());
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
