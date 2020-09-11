// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldMappingData;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class EnumUnboxingCfCodeProvider extends SyntheticCfCodeProvider {

  EnumUnboxingCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  void addCfInstructionsForAbstractValue(
      List<CfInstruction> instructions, AbstractValue value, DexType returnType) {
    // TODO(b/155368026): Support fields and const class fields.
    // Move this to something similar than SingleValue#createMaterializingInstruction
    if (value.isSingleStringValue()) {
      assert returnType == appView.dexItemFactory().stringType;
      instructions.add(new CfConstString(value.asSingleStringValue().getDexString()));
    } else if (value.isSingleNumberValue()) {
      instructions.add(
          new CfConstNumber(
              value.asSingleNumberValue().getValue(), ValueType.fromDexType(returnType)));
    } else {
      throw new Unreachable("Only Number and String fields in enums are supported.");
    }
  }

  public static class EnumUnboxingInstanceFieldCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private final DexType returnType;
    private final EnumValueInfoMap enumValueInfoMap;
    private final EnumInstanceFieldMappingData fieldDataMap;
    private final AbstractValue nullValue;

    public EnumUnboxingInstanceFieldCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexType returnType,
        EnumValueInfoMap enumValueInfoMap,
        EnumInstanceFieldMappingData fieldDataMap,
        AbstractValue nullValue) {
      super(appView, holder);
      this.returnType = returnType;
      this.enumValueInfoMap = enumValueInfoMap;
      this.fieldDataMap = fieldDataMap;
      this.nullValue = nullValue;
    }

    @Override
    public CfCode generateCfCode() {
      // TODO(b/167942775): Should use a table-switch for large enums (maybe same threshold in the
      //  rewriter of switchmaps).
      // Generated static method, for class com.x.MyEnum {A(10),B(20);} would look like:
      // String UtilityClass#com.x.MyEnum_toString(int i) {
      // if (i == 1) { return 10;}
      // if (i == 2) { return 20;}
      // throw null;
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(factory.intType))
              .build();

      // if (i == 1) { return 10;}
      // if (i == 2) { return 20;}
      enumValueInfoMap.forEach(
          (field, enumValueInfo) -> {
            AbstractValue value = fieldDataMap.getData(field);
            if (value != null) {
              CfLabel dest = new CfLabel();
              instructions.add(new CfLoad(ValueType.fromDexType(factory.intType), 0));
              instructions.add(new CfConstNumber(enumValueInfo.convertToInt(), ValueType.INT));
              instructions.add(new CfIfCmp(If.Type.NE, ValueType.INT, dest));
              addCfInstructionsForAbstractValue(instructions, value, returnType);
              instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
              instructions.add(dest);
              instructions.add(new CfFrame(locals, ImmutableDeque.of()));
            }
          });

      if (nullValue != null) {
        // return "null"
        addCfInstructionsForAbstractValue(instructions, nullValue, returnType);
        instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
      } else {
        // throw null;
        instructions.add(new CfConstNull());
        instructions.add(new CfThrow());
      }

      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class EnumUnboxingValueOfCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private DexType enumType;
    private EnumValueInfoMap map;
    private final EnumInstanceFieldMappingData fieldDataMap;

    public EnumUnboxingValueOfCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexType enumType,
        EnumValueInfoMap map,
        EnumInstanceFieldMappingData fieldDataMap) {
      super(appView, holder);
      this.enumType = enumType;
      this.map = map;
      this.fieldDataMap = fieldDataMap;
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

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(factory.stringType))
              .build();

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
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

      // if (s.equals("A")) { return 1;}
      // if (s.equals("B")) { return 2;}
      map.forEach(
          (field, enumValueInfo) -> {
            CfLabel dest = new CfLabel();
            instructions.add(new CfLoad(ValueType.fromDexType(factory.stringType), 0));
            AbstractValue value = fieldDataMap.getData(field);
            addCfInstructionsForAbstractValue(instructions, value, factory.stringType);
            instructions.add(
                new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMembers.equals, false));
            instructions.add(new CfIf(If.Type.EQ, ValueType.INT, dest));
            instructions.add(new CfConstNumber(enumValueInfo.convertToInt(), ValueType.INT));
            instructions.add(new CfReturn(ValueType.INT));
            instructions.add(dest);
            instructions.add(new CfFrame(locals, ImmutableDeque.of()));
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
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMembers.concat, false));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.illegalArgumentExceptionMethods.initWithMessage,
              false));
      instructions.add(new CfThrow());
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class EnumUnboxingValuesCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private final DexField utilityField;
    private final int numEnumInstances;
    private final DexMethod initializationMethod;

    public EnumUnboxingValuesCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexField utilityField,
        int numEnumInstances,
        DexMethod initializationMethod) {
      super(appView, holder);
      assert utilityField.type == appView.dexItemFactory().intArrayType;
      this.utilityField = utilityField;
      this.numEnumInstances = numEnumInstances;
      this.initializationMethod = initializationMethod;
    }

    @Override
    public CfCode generateCfCode() {
      // Generated static method, for class com.x.MyEnum {A,B}, and a field in VALUES$com$x$MyEnum
      // on Utility class, would look like:
      // synchronized int[] UtilityClass#com$x$MyEnum_VALUES() {
      //    if (VALUES$com$x$MyEnum == null) {
      //      VALUES$com$x$MyEnum = EnumUnboxingMethods_values(2);
      //    }
      //    return VALUES$com$x$MyEnum;
      List<CfInstruction> instructions = new ArrayList<>();
      CfLabel nullDest = new CfLabel();
      instructions.add(new CfFieldInstruction(Opcodes.GETSTATIC, utilityField, utilityField));
      instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
      instructions.add((new CfConstNumber(numEnumInstances, ValueType.INT)));
      assert initializationMethod.getArity() == 1;
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, initializationMethod, false));
      instructions.add(new CfFieldInstruction(Opcodes.PUTSTATIC, utilityField, utilityField));
      instructions.add(nullDest);
      instructions.add(
          new CfFrame(
              ImmutableInt2ReferenceSortedMap.<FrameType>builder().build(), ImmutableDeque.of()));
      instructions.add(new CfFieldInstruction(Opcodes.GETSTATIC, utilityField, utilityField));
      instructions.add(new CfReturn(ValueType.OBJECT));
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
