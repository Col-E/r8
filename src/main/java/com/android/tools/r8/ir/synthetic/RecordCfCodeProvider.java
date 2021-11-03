// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfRecordFieldValues;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class RecordCfCodeProvider {

  /**
   * Generates a method which answers all field values as an array of objects. If the field value is
   * a primitive type, it uses the primitive wrapper to wrap it.
   *
   * <p>The fields in parameters are in the order where they should be in the array generated by the
   * method, which is not necessarily the class instanceFields order.
   *
   * <p>Example: <code>record Person{ int age; String name;}</code>
   *
   * <p><code>Object[] getFieldsAsObjects() {
   * Object[] fields = new Object[2];
   * fields[0] = name;
   * fields[1] = Integer.valueOf(age);
   * return fields;</code>
   */
  public static class RecordGetFieldsAsObjectsCfCodeProvider extends SyntheticCfCodeProvider {

    public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
      factory.createSynthesizedType("[Ljava/lang/Object;");
      factory.primitiveToBoxed.forEach(
          (primitiveType, boxedType) -> {
            factory.createSynthesizedType(primitiveType.toDescriptorString());
            factory.createSynthesizedType(boxedType.toDescriptorString());
          });
    }

    private final DexField[] fields;

    public RecordGetFieldsAsObjectsCfCodeProvider(
        AppView<?> appView, DexType holder, DexField[] fields) {
      super(appView, holder);
      this.fields = fields;
    }

    @Override
    public CfCode generateCfCode() {
      // Stack layout:
      // 0 : receiver (the record instance)
      // 1 : the array to return
      // 2+: spills
      return appView.enableWholeProgramOptimizations()
              && appView.options().testing.enableRecordModeling
          ? generateCfCodeWithRecordModeling()
          : generateCfCodeWithArray();
    }

    private CfCode generateCfCodeWithArray() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      // Object[] fields = new Object[*length*];
      instructions.add(new CfConstNumber(fields.length, ValueType.INT));
      instructions.add(new CfNewArray(factory.objectArrayType));
      instructions.add(new CfStore(ValueType.OBJECT, 1));
      // fields[*i*] = this.*field* || *PrimitiveWrapper*.valueOf(this.*field*);
      for (int i = 0; i < fields.length; i++) {
        DexField field = fields[i];
        instructions.add(new CfLoad(ValueType.OBJECT, 1));
        instructions.add(new CfConstNumber(i, ValueType.INT));
        loadFieldAsObject(instructions, field);
        instructions.add(new CfArrayStore(MemberType.OBJECT));
      }
      // return fields;
      instructions.add(new CfLoad(ValueType.OBJECT, 1));
      instructions.add(new CfReturn(ValueType.OBJECT));
      return standardCfCodeFromInstructions(instructions);
    }

    private CfCode generateCfCodeWithRecordModeling() {
      List<CfInstruction> instructions = new ArrayList<>();
      // fields[*i*] = this.*field* || *PrimitiveWrapper*.valueOf(this.*field*);
      for (DexField field : fields) {
        loadFieldAsObject(instructions, field);
      }
      // return recordFieldValues(fields);
      instructions.add(new CfRecordFieldValues(fields));
      instructions.add(new CfReturn(ValueType.OBJECT));
      return standardCfCodeFromInstructions(instructions);
    }

    private void loadFieldAsObject(List<CfInstruction> instructions, DexField field) {
      DexItemFactory factory = appView.dexItemFactory();
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      instructions.add(new CfInstanceFieldRead(field));
      if (field.type.isPrimitiveType()) {
        factory.primitiveToBoxed.forEach(
            (primitiveType, boxedType) -> {
              if (primitiveType == field.type) {
                instructions.add(
                    new CfInvoke(
                        Opcodes.INVOKESTATIC,
                        factory.createMethod(
                            boxedType,
                            factory.createProto(boxedType, primitiveType),
                            factory.valueOfMethodName),
                        false));
              }
            });
      }
    }
  }

  public static class RecordEqualsCfCodeProvider extends SyntheticCfCodeProvider {

    private final DexMethod getFieldsAsObjects;

    public RecordEqualsCfCodeProvider(
        AppView<?> appView, DexType holder, DexMethod getFieldsAsObjects) {
      super(appView, holder);
      this.getFieldsAsObjects = getFieldsAsObjects;
    }

    public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
      factory.createSynthesizedType("[Ljava/lang/Object;");
      factory.createSynthesizedType("[Ljava/util/Arrays;");
    }

    @Override
    public CfCode generateCfCode() {
      // This generates something along the lines of:
      // if (this.getClass() != other.getClass()) {
      //     return false;
      // }
      // return Arrays.equals(
      //     recordInstance.getFieldsAsObjects(),
      //     ((RecordClass) other).getFieldsAsObjects());
      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(getHolder()))
              .put(1, FrameType.initialized(appView.dexItemFactory().objectType))
              .build();
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      CfLabel fieldCmp = new CfLabel();
      ValueType recordType = ValueType.fromDexType(getHolder());
      ValueType objectType = ValueType.fromDexType(factory.objectType);
      instructions.add(new CfLoad(recordType, 0));
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.getClass, false));
      instructions.add(new CfLoad(objectType, 1));
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, factory.objectMembers.getClass, false));
      instructions.add(new CfIfCmp(If.Type.EQ, ValueType.OBJECT, fieldCmp));
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfReturn(ValueType.INT));
      instructions.add(fieldCmp);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));
      instructions.add(new CfLoad(recordType, 0));
      instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
      instructions.add(new CfLoad(objectType, 1));
      instructions.add(new CfCheckCast(getHolder(), true));
      instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC, factory.javaUtilArraysMethods.equalsObjectArray, false));
      instructions.add(new CfReturn(ValueType.INT));
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
