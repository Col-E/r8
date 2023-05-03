// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfSwitch.Kind;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap.EnumData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldMappingData;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.BooleanUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
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
      if (returnType.isReferenceType()) {
        assert value.isNull();
        instructions.add(CfConstNull.INSTANCE);
      } else {
        instructions.add(
            CfConstNumber.constNumber(
                value.asSingleNumberValue().getValue(), ValueType.fromDexType(returnType)));
      }
    } else {
      throw new Unreachable("Only Number and String fields in enums are supported.");
    }
  }

  <T> void addCfSwitch(
      List<CfInstruction> instructions,
      BiConsumer<List<CfInstruction>, T> generateCase,
      Int2ReferenceSortedMap<T> cases,
      T defaultCase,
      CfFrame.Builder frameBuilder,
      boolean defaultThrows) {
    // The switch is *always* going to be converted to IR then either to dex or back to cf. The IR
    // representation does not differentiate table and look-up switches, and generates the most
    // appropriate one in the back-end.
    // The keys should however be sorted in natural order for packing to table switch to be
    // generated, which should be implicitly the case with the Int2ObjectSortedMap.
    assert defaultCase == null || !defaultThrows;
    boolean hasDefaultCase = defaultCase != null || defaultThrows;
    assert cases.size() + BooleanUtils.intValue(hasDefaultCase) >= 2;
    int[] keys = new int[cases.size() - BooleanUtils.intValue(!hasDefaultCase)];
    List<CfLabel> targets = new ArrayList<>(keys.length);
    int index = 0;
    for (int key : cases.keySet()) {
      if (index < keys.length) {
        keys[index++] = key;
        targets.add(new CfLabel());
      }
    }
    CfLabel defaultLabel = new CfLabel();
    T actualDefaultCase = hasDefaultCase ? defaultCase : cases.get(cases.lastIntKey());
    assert ArrayUtils.isSorted(keys);
    assert keys.length == targets.size();
    // We expect the value to switch on to be in local slot 0.
    instructions.add(CfLoad.load(ValueType.fromDexType(appView.dexItemFactory().intType), 0));
    instructions.add(new CfSwitch(Kind.LOOKUP, defaultLabel, keys, targets));
    for (int i = 0; i < keys.length; i++) {
      instructions.add(targets.get(i));
      instructions.add(frameBuilder.build());
      generateCase.accept(instructions, cases.get(keys[i]));
      assert instructions.get(instructions.size() - 1).isReturn();
    }
    instructions.add(defaultLabel);
    instructions.add(frameBuilder.build());
    if (defaultThrows) {
      // default: throw null;
      instructions.add(CfConstNull.INSTANCE);
      instructions.add(CfThrow.INSTANCE);
    } else {
      generateCase.accept(instructions, actualDefaultCase);
      assert instructions.get(instructions.size() - 1).isReturn();
    }
  }

  public static class EnumUnboxingMethodDispatchCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private final GraphLens codeLens;
    private final DexMethod superEnumMethod;
    private final Int2ReferenceSortedMap<DexMethod> methodMap;

    public EnumUnboxingMethodDispatchCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexMethod superEnumMethod,
        Int2ReferenceSortedMap<DexMethod> methodMap) {
      super(appView, holder);
      this.codeLens = appView.codeLens();
      this.superEnumMethod = superEnumMethod;
      this.methodMap = methodMap;
    }

    @Override
    public CfCodeWithLens generateCfCode() {
      assert !methodMap.isEmpty();
      List<CfInstruction> instructions = new ArrayList<>();
      DexMethod representative = methodMap.values().iterator().next();
      CfFrame.Builder frameBuilder = CfFrame.builder();
      for (DexType parameter : representative.getParameters()) {
        frameBuilder.appendLocal(FrameType.initialized(parameter));
      }
      addCfSwitch(
          instructions, this::addReturnInvoke, methodMap, superEnumMethod, frameBuilder, false);
      return new CfCodeWithLens(getHolder(), defaultMaxStack(), defaultMaxLocals(), instructions);
    }

    private void addReturnInvoke(List<CfInstruction> instructions, DexMethod method) {
      int localIndex = 0;
      for (DexType parameterType : method.getParameters()) {
        instructions.add(CfLoad.load(ValueType.fromDexType(parameterType), localIndex));
        localIndex += parameterType.getRequiredRegisters();
      }
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
      instructions.add(
          method.getReturnType().isVoidType()
              ? CfReturnVoid.INSTANCE
              : CfReturn.forType(ValueType.fromDexType(method.getReturnType())));
    }

    public static class CfCodeWithLens extends CfCode {
      private GraphLens codeLens;

      public void setCodeLens(GraphLens codeLens) {
        this.codeLens = codeLens;
      }

      public CfCodeWithLens(
          DexType originalHolder, int maxStack, int maxLocals, List<CfInstruction> instructions) {
        super(originalHolder, maxStack, maxLocals, instructions);
      }

      @Override
      public GraphLens getCodeLens(AppView<?> appView) {
        assert codeLens != null;
        return codeLens;
      }
    }
  }

  public static class EnumUnboxingInstanceFieldCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private final DexType returnType;
    private final EnumInstanceFieldMappingData fieldDataMap;
    private final AbstractValue nullValue;

    public EnumUnboxingInstanceFieldCfCodeProvider(
        AppView<?> appView, DexType holder, EnumData data, DexField field) {
      this(appView, holder, data, field, null);
    }

    public EnumUnboxingInstanceFieldCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        EnumData data,
        DexField field,
        AbstractValue nullValue) {
      super(appView, holder);
      this.returnType = field.getType();
      this.fieldDataMap = data.getInstanceFieldData(field).asEnumFieldMappingData();
      this.nullValue = nullValue;
    }

    @Override
    public CfCode generateCfCode() {
      // Generated static method, for class com.x.MyEnum {A(10),B(20);} would look like:
      // String UtilityClass#com.x.MyEnum_toString(int i) {
      //   switch (i) {
      //     case 1: return 10;
      //     case 2: return 20;
      //     default: throw null; // or throw default value.
      //   }
      // }
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      CfFrame.Builder frameBuilder =
          CfFrame.builder().appendLocal(FrameType.initialized(factory.intType));
      addCfSwitch(
          instructions,
          this::addReturnValue,
          fieldDataMap.getMapping(),
          nullValue,
          frameBuilder,
          nullValue == null);
      return standardCfCodeFromInstructions(instructions);
    }

    private void addReturnValue(List<CfInstruction> instructions, AbstractValue value) {
      addCfInstructionsForAbstractValue(instructions, value, returnType);
      instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
    }
  }

  public static class EnumUnboxingValueOfCfCodeProvider extends EnumUnboxingCfCodeProvider {

    private final DexType enumType;
    private final EnumInstanceFieldMappingData fieldDataMap;

    public EnumUnboxingValueOfCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        DexType enumType,
        EnumInstanceFieldMappingData fieldDataMap) {
      super(appView, holder);
      this.enumType = enumType;
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

      CfFrame frame =
          CfFrame.builder().appendLocal(FrameType.initialized(factory.stringType)).build();

      // if (s == null) { throw npe("Name is null"); }
      CfLabel nullDest = new CfLabel();
      instructions.add(CfLoad.load(ValueType.fromDexType(factory.stringType), 0));
      instructions.add(new CfIf(IfType.NE, ValueType.OBJECT, nullDest));
      instructions.add(new CfNew(factory.npeType));
      instructions.add(CfStackInstruction.DUP);
      instructions.add(new CfConstString(appView.dexItemFactory().createString("Name is null")));
      instructions.add(
          new CfInvoke(Opcodes.INVOKESPECIAL, factory.npeMethods.initWithMessage, false));
      instructions.add(CfThrow.INSTANCE);
      instructions.add(nullDest);
      instructions.add(frame);

      // if (s.equals("A")) { return 1;}
      // if (s.equals("B")) { return 2;}
      fieldDataMap.forEach(
          (unboxedEnumValue, value) -> {
            CfLabel dest = new CfLabel();
            instructions.add(CfLoad.load(ValueType.fromDexType(factory.stringType), 0));
            addCfInstructionsForAbstractValue(instructions, value, factory.stringType);
            instructions.add(
                new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMembers.equals, false));
            instructions.add(new CfIf(IfType.EQ, ValueType.INT, dest));
            instructions.add(CfConstNumber.constNumber(unboxedEnumValue, ValueType.INT));
            instructions.add(CfReturn.IRETURN);
            instructions.add(dest);
            instructions.add(frame.clone());
          });

      // throw new IllegalArgumentException("No enum constant com.x.MyEnum." + s);
      instructions.add(new CfNew(factory.illegalArgumentExceptionType));
      instructions.add(CfStackInstruction.DUP);
      instructions.add(
          new CfConstString(
              appView
                  .dexItemFactory()
                  .createString(
                      "No enum constant " + enumType.toSourceString().replace('$', '.') + ".")));
      instructions.add(CfLoad.load(ValueType.fromDexType(factory.stringType), 0));
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.stringMembers.concat, false));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.illegalArgumentExceptionMethods.initWithMessage,
              false));
      instructions.add(CfThrow.INSTANCE);
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
      instructions.add(new CfStaticFieldRead(utilityField, utilityField));
      instructions.add(new CfIf(IfType.NE, ValueType.OBJECT, nullDest));
      instructions.add((CfConstNumber.constNumber(numEnumInstances, ValueType.INT)));
      assert initializationMethod.getArity() == 1;
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, initializationMethod, false));
      instructions.add(new CfStaticFieldWrite(utilityField, utilityField));
      instructions.add(nullDest);
      instructions.add(new CfFrame());
      instructions.add(new CfStaticFieldRead(utilityField, utilityField));
      instructions.add(CfReturn.ARETURN);
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
