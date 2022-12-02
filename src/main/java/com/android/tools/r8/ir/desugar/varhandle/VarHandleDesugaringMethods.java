// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateVarHandleMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.varhandle;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.google.common.collect.ImmutableList;

public final class VarHandleDesugaringMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Lcom/android/tools/r8/DesugarVarHandle;");
    factory.createSynthesizedType("Ljava/lang/reflect/Field;");
    factory.createSynthesizedType("Lsun/misc/Unsafe;");
  }

  public static void generateDesugarVarHandleClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setInstanceFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(factory.createString("Lsun/misc/Unsafe;")),
                        factory.createString("U")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(factory.createString("Ljava/lang/Class;")),
                        factory.createString("recv")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(),
                        factory.createType(factory.createString("Ljava/lang/Class;")),
                        factory.createString("type")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(
                    factory.createField(
                        builder.getType(), factory.longType, factory.createString("offset")))
                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
    DexMethod set =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType, factory.objectType, factory.objectType),
            factory.createString("set"));
    DexMethod get =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.objectType, factory.objectType),
            factory.createString("get"));
    DexMethod compareAndSetInt =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType, factory.objectType, factory.intType, factory.intType),
            factory.createString("compareAndSet"));
    DexMethod getInt =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.intType, factory.objectType),
            factory.createString("get"));
    DexMethod compareAndSet =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType, factory.objectType, factory.objectType, factory.objectType),
            factory.createString("compareAndSet"));
    DexMethod setInt =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType, factory.objectType, factory.intType),
            factory.createString("set"));
    DexMethod getLong =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.longType, factory.objectType),
            factory.createString("get"));
    DexMethod constructor_3 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.voidType,
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("<init>"));
    DexMethod setLong =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType, factory.objectType, factory.longType),
            factory.createString("set"));
    DexMethod compareAndSetLong =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.booleanType, factory.objectType, factory.longType, factory.longType),
            factory.createString("compareAndSet"));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_3)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(DesugarVarHandle_constructor_3(factory, constructor_3))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(set)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_set(factory, set))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(get)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_get(factory, get))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(compareAndSetInt)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_compareAndSetInt(factory, compareAndSetInt))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getInt)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_getInt(factory, getInt))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(compareAndSet)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_compareAndSet(factory, compareAndSet))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(setInt)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_setInt(factory, setInt))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getLong)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_getLong(factory, getLong))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(setLong)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_setLong(factory, setLong))
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedMethod.syntheticBuilder()
                .setMethod(compareAndSetLong)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarVarHandle_compareAndSetLong(factory, compareAndSetLong))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static void generateDesugarMethodHandlesLookupClass(
      SyntheticProgramClassBuilder builder, DexItemFactory factory) {
    builder.setInstanceFields(ImmutableList.of());
    DexMethod findVarHandle =
        factory.createMethod(
            builder.getType(),
            factory.createProto(
                factory.createType(factory.createString("Lcom/android/tools/r8/DesugarVarHandle;")),
                factory.createType(factory.createString("Ljava/lang/Class;")),
                factory.createType(factory.createString("Ljava/lang/String;")),
                factory.createType(factory.createString("Ljava/lang/Class;"))),
            factory.createString("findVarHandle"));
    DexMethod constructor_0 =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.createString("<init>"));
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(constructor_0)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true))
                .setCode(DesugarMethodHandlesLookup_constructor_0(factory, constructor_0))
                .disableAndroidApiLevelCheck()
                .build()));
    builder.setVirtualMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(findVarHandle)
                .setAccessFlags(
                    MethodAccessFlags.fromSharedAccessFlags(
                        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false))
                .setCode(DesugarMethodHandlesLookup_findVarHandle(factory, findVarHandle))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  public static CfCode DesugarMethodHandlesLookup_constructor_0(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfReturnVoid(),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarMethodHandlesLookup_findVarHandle(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Lcom/android/tools/r8/DesugarVarHandle;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.createProto(
                        factory.voidType, factory.classType, factory.stringType, factory.classType),
                    factory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_constructor_3(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            label1,
            new CfConstClass(factory.createType("Lsun/misc/Unsafe;")),
            new CfConstString(factory.createString("theUnsafe")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getDeclaredField")),
                false),
            new CfStore(ValueType.OBJECT, 4),
            label2,
            new CfLoad(ValueType.OBJECT, 4),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.voidType, factory.booleanType),
                    factory.createString("setAccessible")),
                false),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 4),
            new CfConstNull(),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("get")),
                false),
            new CfCheckCast(factory.createType("Lsun/misc/Unsafe;")),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createString("U"))),
            label4,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.classType,
                    factory.createString("recv"))),
            label5,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getDeclaredField")),
                false),
            new CfStore(ValueType.OBJECT, 5),
            label6,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.classType),
                    factory.createString("getType")),
                false),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.classType,
                    factory.createString("type"))),
            label7,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createString("U"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getDeclaredField")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createProto(
                        factory.longType, factory.createType("Ljava/lang/reflect/Field;")),
                    factory.createString("objectFieldOffset")),
                false),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/DesugarVarHandle;"),
                    factory.longType,
                    factory.createString("offset"))),
            label8,
            new CfReturnVoid(),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_compareAndSet(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        4,
        ImmutableList.of(
            label0, new CfConstNumber(0, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_compareAndSetInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        4,
        ImmutableList.of(
            label0, new CfConstNumber(0, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_compareAndSetLong(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        6,
        ImmutableList.of(
            label0, new CfConstNumber(0, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_get(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(label0, new CfConstNull(), new CfReturn(ValueType.OBJECT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_getInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(
            label0, new CfConstNumber(-1, ValueType.INT), new CfReturn(ValueType.INT), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_getLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0, new CfConstNumber(-1, ValueType.LONG), new CfReturn(ValueType.LONG), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_set(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_setInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        3,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DesugarVarHandle_setLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        0,
        4,
        ImmutableList.of(label0, new CfReturnVoid(), label1),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
