// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateBackportMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfCmp;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfNeg;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class BackportedMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/ArithmeticException;");
    factory.createSynthesizedType("Ljava/lang/AssertionError;");
    factory.createSynthesizedType("Ljava/lang/Double;");
    factory.createSynthesizedType("Ljava/lang/Exception;");
    factory.createSynthesizedType("Ljava/lang/ExceptionInInitializerError;");
    factory.createSynthesizedType("Ljava/lang/Float;");
    factory.createSynthesizedType("Ljava/lang/IllegalAccessException;");
    factory.createSynthesizedType("Ljava/lang/IllegalArgumentException;");
    factory.createSynthesizedType("Ljava/lang/IndexOutOfBoundsException;");
    factory.createSynthesizedType("Ljava/lang/Integer;");
    factory.createSynthesizedType("Ljava/lang/Iterable;");
    factory.createSynthesizedType("Ljava/lang/Long;");
    factory.createSynthesizedType("Ljava/lang/Math;");
    factory.createSynthesizedType("Ljava/lang/NoSuchMethodException;");
    factory.createSynthesizedType("Ljava/lang/NullPointerException;");
    factory.createSynthesizedType("Ljava/lang/NumberFormatException;");
    factory.createSynthesizedType("Ljava/lang/Runnable;");
    factory.createSynthesizedType("Ljava/lang/SecurityException;");
    factory.createSynthesizedType("Ljava/lang/reflect/InvocationTargetException;");
    factory.createSynthesizedType("Ljava/lang/reflect/Method;");
    factory.createSynthesizedType("Ljava/util/AbstractMap$SimpleImmutableEntry;");
    factory.createSynthesizedType("Ljava/util/ArrayList;");
    factory.createSynthesizedType("Ljava/util/Arrays;");
    factory.createSynthesizedType("Ljava/util/Collection;");
    factory.createSynthesizedType("Ljava/util/Collections;");
    factory.createSynthesizedType("Ljava/util/Comparator;");
    factory.createSynthesizedType("Ljava/util/Enumeration;");
    factory.createSynthesizedType("Ljava/util/HashMap;");
    factory.createSynthesizedType("Ljava/util/HashSet;");
    factory.createSynthesizedType("Ljava/util/Iterator;");
    factory.createSynthesizedType("Ljava/util/List;");
    factory.createSynthesizedType("Ljava/util/ListIterator;");
    factory.createSynthesizedType("Ljava/util/Map$Entry;");
    factory.createSynthesizedType("Ljava/util/Map;");
    factory.createSynthesizedType("Ljava/util/Objects;");
    factory.createSynthesizedType("Ljava/util/Optional;");
    factory.createSynthesizedType("Ljava/util/OptionalDouble;");
    factory.createSynthesizedType("Ljava/util/OptionalInt;");
    factory.createSynthesizedType("Ljava/util/OptionalLong;");
    factory.createSynthesizedType("Ljava/util/Set;");
    factory.createSynthesizedType("Ljava/util/function/Consumer;");
    factory.createSynthesizedType("Ljava/util/function/DoubleConsumer;");
    factory.createSynthesizedType("Ljava/util/function/IntConsumer;");
    factory.createSynthesizedType("Ljava/util/function/LongConsumer;");
    factory.createSynthesizedType("Ljava/util/function/Supplier;");
    factory.createSynthesizedType("Ljava/util/stream/DoubleStream;");
    factory.createSynthesizedType("Ljava/util/stream/IntStream;");
    factory.createSynthesizedType("Ljava/util/stream/LongStream;");
    factory.createSynthesizedType("Ljava/util/stream/Stream;");
    factory.createSynthesizedType("[Ljava/lang/CharSequence;");
    factory.createSynthesizedType("[Ljava/lang/Class;");
    factory.createSynthesizedType("[Ljava/lang/Object;");
    factory.createSynthesizedType("[Ljava/util/Map$Entry;");
  }

  public static CfCode BooleanMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label3),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(-1, ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_hashCode(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.EQ, ValueType.INT, label1),
            new CfConstNumber(1231, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1237, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_compareUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(255, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(255, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_toUnsignedInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(255, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_toUnsignedLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(255, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharSequenceMethods_compare(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        8,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.charSequenceType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                true),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.charSequenceType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                true),
            new CfStore(ValueType.INT, 3),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.NE, ValueType.OBJECT, label4),
            label3,
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label5,
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("min")),
                false),
            new CfStore(ValueType.INT, 5),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 5),
            new CfIfCmp(If.Type.GE, ValueType.INT, label12),
            label7,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 4),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.charSequenceType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType, options.itemFactory.intType),
                    options.itemFactory.createString("charAt")),
                true),
            new CfStore(ValueType.INT, 6),
            label8,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 4),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.charSequenceType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType, options.itemFactory.intType),
                    options.itemFactory.createString("charAt")),
                true),
            new CfStore(ValueType.INT, 7),
            label9,
            new CfLoad(ValueType.INT, 6),
            new CfLoad(ValueType.INT, 7),
            new CfIfCmp(If.Type.EQ, ValueType.INT, label11),
            label10,
            new CfLoad(ValueType.INT, 6),
            new CfLoad(ValueType.INT, 7),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label11,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfIinc(4, 1),
            new CfGoto(label6),
            label12,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 3),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label13),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharacterMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharacterMethods_toStringCodepoint(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.stringType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.charArrayType, options.itemFactory.intType),
                    options.itemFactory.createString("toChars")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.charArrayType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CloseResourceMethod_closeResourceImpl(
      InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.autoCloseableType),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.autoCloseableType),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.autoCloseableType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("close")),
                true),
            new CfGoto(label11),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(options.itemFactory.classType),
                    options.itemFactory.createString("getClass")),
                false),
            new CfConstString(options.itemFactory.createString("close")),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(options.itemFactory.createType("[Ljava/lang/Class;")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.classType,
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/lang/reflect/Method;"),
                        options.itemFactory.stringType,
                        options.itemFactory.createType("[Ljava/lang/Class;")),
                    options.itemFactory.createString("getMethod")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label3,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(0, ValueType.INT),
            new CfNewArray(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/reflect/Method;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.createType("[Ljava/lang/Object;")),
                    options.itemFactory.createString("invoke")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfGoto(label11),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(
                            options.itemFactory.createType("Ljava/lang/Exception;"))))),
            new CfStore(ValueType.OBJECT, 2),
            label6,
            new CfNew(options.itemFactory.createType("Ljava/lang/AssertionError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(options.itemFactory.classType),
                    options.itemFactory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.objectType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" does not have a close() method.")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/AssertionError;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType,
                        options.itemFactory.stringType,
                        options.itemFactory.throwableType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.throwableType)))),
            new CfStore(ValueType.OBJECT, 2),
            label8,
            new CfNew(options.itemFactory.createType("Ljava/lang/AssertionError;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Fail to call close() on ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(options.itemFactory.classType),
                    options.itemFactory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.objectType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/AssertionError;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType,
                        options.itemFactory.stringType,
                        options.itemFactory.throwableType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label9,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(
                            options.itemFactory.createType(
                                "Ljava/lang/reflect/InvocationTargetException;"))))),
            new CfStore(ValueType.OBJECT, 2),
            label10,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/reflect/InvocationTargetException;"),
                    options.itemFactory.createProto(options.itemFactory.throwableType),
                    options.itemFactory.createString("getCause")),
                false),
            new CfThrow(),
            label11,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfGoto(label16),
            label12,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.throwableType)))),
            new CfStore(ValueType.OBJECT, 2),
            label13,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label14),
            new CfLoad(ValueType.OBJECT, 0),
            new CfGoto(label15),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.throwableType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            label15,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.throwableType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.throwableType)))),
            new CfThrow(),
            label16,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.throwableType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label17),
        ImmutableList.of(
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    options.itemFactory.createType("Ljava/lang/NoSuchMethodException;")),
                ImmutableList.of(label5)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(options.itemFactory.createType("Ljava/lang/SecurityException;")),
                ImmutableList.of(label5)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    options.itemFactory.createType("Ljava/lang/IllegalAccessException;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    options.itemFactory.createType("Ljava/lang/IllegalArgumentException;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    options.itemFactory.createType("Ljava/lang/ExceptionInInitializerError;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    options.itemFactory.createType(
                        "Ljava/lang/reflect/InvocationTargetException;")),
                ImmutableList.of(label9)),
            new CfTryCatch(
                label0,
                label11,
                ImmutableList.of(options.itemFactory.throwableType),
                ImmutableList.of(label12))),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_listOfArray(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        6,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/ArrayList;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfArrayLength(),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/ArrayList;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfStore(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 3),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/ArrayList;")),
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 3),
            new CfIfCmp(If.Type.GE, ValueType.INT, label5),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.INT, 4),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 5),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/ArrayList;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("add")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfIinc(4, 1),
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/ArrayList;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/List;"),
                        options.itemFactory.createType("Ljava/util/List;")),
                    options.itemFactory.createString("unmodifiableList")),
                false),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_mapEntry(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfNew(
                options.itemFactory.createType("Ljava/util/AbstractMap$SimpleImmutableEntry;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            label1,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfLoad(ValueType.OBJECT, 1),
            label2,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/AbstractMap$SimpleImmutableEntry;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType,
                        options.itemFactory.objectType,
                        options.itemFactory.objectType),
                    options.itemFactory.createString("<init>")),
                false),
            label3,
            new CfReturn(ValueType.OBJECT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_mapOfEntries(InternalOptions options, DexMethod method) {
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
        8,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/HashMap;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfArrayLength(),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashMap;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfStore(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 3),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashMap;")),
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 3),
            new CfIfCmp(If.Type.GE, ValueType.INT, label8),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.INT, 4),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 5),
            label3,
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map$Entry;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("getKey")),
                true),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfStore(ValueType.OBJECT, 6),
            label4,
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map$Entry;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("getValue")),
                true),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfStore(ValueType.OBJECT, 7),
            label5,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 6),
            new CfLoad(ValueType.OBJECT, 7),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashMap;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.objectType),
                    options.itemFactory.createString("put")),
                false),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label7),
            label6,
            new CfNew(options.itemFactory.createType("Ljava/lang/IllegalArgumentException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("duplicate key: ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 6),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.objectType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IllegalArgumentException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashMap;")),
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfIinc(4, 1),
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashMap;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Map;"),
                        options.itemFactory.createType("Ljava/util/Map;")),
                    options.itemFactory.createString("unmodifiableMap")),
                false),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_setOfArray(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        6,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/HashSet;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfArrayLength(),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashSet;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfStore(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 3),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashSet;")),
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 3),
            new CfIfCmp(If.Type.GE, ValueType.INT, label6),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.INT, 4),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 5),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashSet;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("add")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label5),
            label4,
            new CfNew(options.itemFactory.createType("Ljava/lang/IllegalArgumentException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("duplicate element: ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.objectType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IllegalArgumentException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashSet;")),
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfIinc(4, 1),
            new CfGoto(label2),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("[Ljava/lang/Object;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashSet;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Set;"),
                        options.itemFactory.createType("Ljava/util/Set;")),
                    options.itemFactory.createString("unmodifiableSet")),
                false),
            new CfReturn(ValueType.OBJECT),
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfList(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/ArrayList;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collection;"),
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("size")),
                true),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/ArrayList;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collection;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Collection;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/ArrayList;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Iterator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label5),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("next")),
                true),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/ArrayList;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("add")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Collection;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/ArrayList;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/List;"),
                        options.itemFactory.createType("Ljava/util/List;")),
                    options.itemFactory.createString("unmodifiableList")),
                false),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfMap(InternalOptions options, DexMethod method) {
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
        3,
        4,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/HashMap;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map;"),
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("size")),
                true),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashMap;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Set;")),
                    options.itemFactory.createString("entrySet")),
                true),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Set;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Map;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashMap;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Iterator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("next")),
                true),
            new CfCheckCast(options.itemFactory.createType("Ljava/util/Map$Entry;")),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 3),
            label4,
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map$Entry;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("getKey")),
                true),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfLoad(ValueType.OBJECT, 3),
            label5,
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Map$Entry;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("getValue")),
                true),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            label6,
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashMap;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.objectType),
                    options.itemFactory.createString("put")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Map;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashMap;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Map;"),
                        options.itemFactory.createType("Ljava/util/Map;")),
                    options.itemFactory.createString("unmodifiableMap")),
                false),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfSet(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfNew(options.itemFactory.createType("Ljava/util/HashSet;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collection;"),
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("size")),
                true),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashSet;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collection;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Collection;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashSet;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Iterator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label5),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("next")),
                true),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/HashSet;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("add")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Collection;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/HashSet;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Set;"),
                        options.itemFactory.createType("Ljava/util/Set;")),
                    options.itemFactory.createString("unmodifiableSet")),
                false),
            new CfReturn(ValueType.OBJECT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyEnumeration(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/List;")),
                    options.itemFactory.createString("emptyList")),
                false),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Enumeration;"),
                        options.itemFactory.createType("Ljava/util/Collection;")),
                    options.itemFactory.createString("enumeration")),
                false),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyIterator(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/List;")),
                    options.itemFactory.createString("emptyList")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/List;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyListIterator(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Collections;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/List;")),
                    options.itemFactory.createString("emptyList")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/List;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/ListIterator;")),
                    options.itemFactory.createString("listIterator")),
                true),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DoubleMethods_hashCode(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.DOUBLE, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Double;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType, options.itemFactory.doubleType),
                    options.itemFactory.createString("doubleToLongBits")),
                false),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DoubleMethods_isFinite(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.DOUBLE, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Double;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.doubleType),
                    options.itemFactory.createString("isInfinite")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfLoad(ValueType.DOUBLE, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Double;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.doubleType),
                    options.itemFactory.createString("isNaN")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.doubleType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.doubleType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode FloatMethods_isFinite(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.FLOAT, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Float;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.floatType),
                    options.itemFactory.createString("isInfinite")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfLoad(ValueType.FLOAT, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Float;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.floatType),
                    options.itemFactory.createString("isNaN")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.floatType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.floatType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label3),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.GE, ValueType.INT, label2),
            new CfConstNumber(-1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_compareUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(-2147483648, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(-2147483648, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT),
            new CfStore(ValueType.INT, 3),
            label2,
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Integer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("compare")),
                false),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_divideUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.LONG, 4),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseUnsignedInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Integer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.stringType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("parseUnsignedInt")),
                false),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseUnsignedIntWithRadix(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfConstNumber(1, ValueType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType, options.itemFactory.intType),
                    options.itemFactory.createString("charAt")),
                false),
            new CfConstNumber(43, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType, options.itemFactory.intType),
                    options.itemFactory.createString("substring")),
                false),
            new CfStore(ValueType.OBJECT, 0),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.stringType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("parseLong")),
                false),
            new CfStore(ValueType.LONG, 2),
            label3,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfLoad(ValueType.LONG, 2),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.EQ, ValueType.INT, label5),
            label4,
            new CfNew(options.itemFactory.createType("Ljava/lang/NumberFormatException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Input ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" in base ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(
                options.itemFactory.createString(" is not in the range of an unsigned integer")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NumberFormatException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 2),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_remainderUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.LONG, 4),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.LONG),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedString(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Integer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("toUnsignedString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedStringWithRadix(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.longType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_compareUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        8,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label2,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 6),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("compare")),
                false),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_divideUnsigned(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        12,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label6),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label3,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 6),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label5),
            label4,
            new CfConstNumber(0, ValueType.LONG),
            new CfReturn(ValueType.LONG),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.LONG),
            new CfReturn(ValueType.LONG),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label8),
            label7,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shl, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label9,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label10,
            new CfLoad(ValueType.LONG, 6),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 8),
            label11,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 10),
            label12,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 8),
            new CfLoad(ValueType.LONG, 10),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label13),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label14),
            label13,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8, 10},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.longType)))),
            new CfConstNumber(0, ValueType.INT),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8, 10},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(options.itemFactory.longType),
                        FrameType.initialized(options.itemFactory.intType)))),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_hashCode(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseUnsignedLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.stringType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("parseUnsignedLong")),
                false),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseUnsignedLongWithRadix(
      InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    CfLabel label19 = new CfLabel();
    CfLabel label20 = new CfLabel();
    CfLabel label21 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        10,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.INT, 2),
            new CfIf(If.Type.NE, ValueType.INT, label3),
            label2,
            new CfNew(options.itemFactory.createType("Ljava/lang/NumberFormatException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("empty string")),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NumberFormatException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(2, ValueType.INT),
            new CfIfCmp(If.Type.LT, ValueType.INT, label4),
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(36, ValueType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label5),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/NumberFormatException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("illegal radix: ")),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType, options.itemFactory.intType),
                    options.itemFactory.createString("valueOf")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType, options.itemFactory.stringType),
                    options.itemFactory.createString("concat")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NumberFormatException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(-1, ValueType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("divideUnsigned")),
                false),
            new CfStore(ValueType.LONG, 3),
            label6,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType, options.itemFactory.intType),
                    options.itemFactory.createString("charAt")),
                false),
            new CfConstNumber(43, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label7),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label7),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label8),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfStore(ValueType.INT, 5),
            label9,
            new CfConstNumber(0, ValueType.LONG),
            new CfStore(ValueType.LONG, 6),
            label10,
            new CfLoad(ValueType.INT, 5),
            new CfStore(ValueType.INT, 8),
            label11,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 5, 6, 8},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 8),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.GE, ValueType.INT, label20),
            label12,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 8),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType, options.itemFactory.intType),
                    options.itemFactory.createString("charAt")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.charType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("digit")),
                false),
            new CfStore(ValueType.INT, 9),
            label13,
            new CfLoad(ValueType.INT, 9),
            new CfConstNumber(-1, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label15),
            label14,
            new CfNew(options.itemFactory.createType("Ljava/lang/NumberFormatException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NumberFormatException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label15,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 5, 6, 8, 9},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 6),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label17),
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.LONG, 3),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GT, ValueType.INT, label17),
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.LONG, 3),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label18),
            new CfLoad(ValueType.INT, 9),
            new CfConstNumber(-1, ValueType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            label16,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("remainderUnsigned")),
                false),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label18),
            label17,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 5, 6, 8, 9},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/NumberFormatException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("Too large for unsigned long: ")),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType, options.itemFactory.stringType),
                    options.itemFactory.createString("concat")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NumberFormatException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label18,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 5, 6, 8, 9},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfLoad(ValueType.INT, 9),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label19,
            new CfIinc(8, 1),
            new CfGoto(label11),
            label20,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 5, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 6),
            new CfReturn(ValueType.LONG),
            label21),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_remainderUnsigned(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        12,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label6),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label3,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 6),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label5),
            label4,
            new CfLoad(ValueType.LONG, 0),
            new CfReturn(ValueType.LONG),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label8),
            label7,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shl, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label9,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label10,
            new CfLoad(ValueType.LONG, 6),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 8),
            label11,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfStore(ValueType.LONG, 10),
            label12,
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.LONG, 8),
            new CfLoad(ValueType.LONG, 10),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label13),
            new CfLoad(ValueType.LONG, 2),
            new CfGoto(label14),
            label13,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8, 10},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.longType)))),
            new CfConstNumber(0, ValueType.LONG),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8, 10},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(options.itemFactory.longType),
                        FrameType.initialized(options.itemFactory.longType)))),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_toUnsignedString(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.longType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("toUnsignedString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_toUnsignedStringWithRadix(
      InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    CfLabel label19 = new CfLabel();
    CfLabel label20 = new CfLabel();
    CfLabel label21 = new CfLabel();
    CfLabel label22 = new CfLabel();
    CfLabel label23 = new CfLabel();
    CfLabel label24 = new CfLabel();
    CfLabel label25 = new CfLabel();
    CfLabel label26 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        9,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfConstString(options.itemFactory.createString("0")),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.longType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(2, ValueType.INT),
            new CfIfCmp(If.Type.LT, ValueType.INT, label5),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(36, ValueType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label6),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(10, ValueType.INT),
            new CfStore(ValueType.INT, 2),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(64, ValueType.INT),
            new CfNewArray(options.itemFactory.charArrayType),
            new CfStore(ValueType.OBJECT, 3),
            label7,
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 4),
            label8,
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfIf(If.Type.NE, ValueType.INT, label15),
            label9,
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Integer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("numberOfTrailingZeros")),
                false),
            new CfStore(ValueType.INT, 5),
            label10,
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfStore(ValueType.INT, 6),
            label11,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 3),
            new CfIinc(4, -1),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.LONG, 0),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfLoad(ValueType.INT, 6),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("forDigit")),
                false),
            new CfArrayStore(MemberType.CHAR),
            label12,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 5),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfStore(ValueType.LONG, 0),
            label13,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label11),
            label14,
            new CfGoto(label25),
            label15,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfIf(If.Type.NE, ValueType.INT, label18),
            label16,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.INT),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfStore(ValueType.LONG, 5),
            label17,
            new CfGoto(label19),
            label18,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("divideUnsigned")),
                false),
            new CfStore(ValueType.LONG, 5),
            label19,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 5),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 7),
            label20,
            new CfLoad(ValueType.OBJECT, 3),
            new CfIinc(4, -1),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.LONG, 7),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("forDigit")),
                false),
            new CfArrayStore(MemberType.CHAR),
            label21,
            new CfLoad(ValueType.LONG, 5),
            new CfStore(ValueType.LONG, 0),
            label22,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4, 5, 7},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LE, ValueType.INT, label25),
            label23,
            new CfLoad(ValueType.OBJECT, 3),
            new CfIinc(4, -1),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.LONG),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.charType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("forDigit")),
                false),
            new CfArrayStore(MemberType.CHAR),
            label24,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfStore(ValueType.LONG, 0),
            new CfGoto(label22),
            label25,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.charArrayType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.stringType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.OBJECT, 3),
            new CfArrayLength(),
            new CfLoad(ValueType.INT, 4),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.voidType,
                        options.itemFactory.charArrayType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT),
            label26),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_addExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.INT, 4),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.INT, 4),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_addExactLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label2),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 4),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label4),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfConstNumber(0, ValueType.INT),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(options.itemFactory.intType),
                        FrameType.initialized(options.itemFactory.intType)))),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.INT),
            new CfIf(If.Type.EQ, ValueType.INT, label7),
            label6,
            new CfLoad(ValueType.LONG, 4),
            new CfReturn(ValueType.LONG),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_decrementExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(-2147483648, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_decrementExactLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.longType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(1, ValueType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.INT),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfStore(ValueType.INT, 3),
            label2,
            new CfLoad(ValueType.INT, 3),
            new CfIf(If.Type.NE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.INT, 2),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT),
            new CfConstNumber(31, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.INT),
            new CfStore(ValueType.INT, 4),
            label5,
            new CfLoad(ValueType.INT, 4),
            new CfIf(If.Type.GE, ValueType.INT, label6),
            new CfLoad(ValueType.INT, 2),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfGoto(label7),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        10,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.LONG, 4),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label2,
            new CfLoad(ValueType.LONG, 6),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.LONG, 4),
            new CfReturn(ValueType.LONG),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.LONG),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(63, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.LONG),
            new CfStore(ValueType.LONG, 8),
            label5,
            new CfLoad(ValueType.LONG, 8),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.GE, ValueType.INT, label6),
            new CfLoad(ValueType.LONG, 4),
            new CfConstNumber(1, ValueType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfGoto(label7),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 4),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6, 8},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.longType)))),
            new CfReturn(ValueType.LONG),
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivLongInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("floorDiv")),
                false),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.INT),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.INT, 2),
            new CfIf(If.Type.NE, ValueType.INT, label3),
            label2,
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.INT),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT),
            new CfConstNumber(31, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.INT),
            new CfStore(ValueType.INT, 3),
            label4,
            new CfLoad(ValueType.INT, 3),
            new CfIf(If.Type.LE, ValueType.INT, label5),
            new CfLoad(ValueType.INT, 2),
            new CfGoto(label6),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        8,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Rem, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 4),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label3),
            label2,
            new CfConstNumber(0, ValueType.LONG),
            new CfReturn(ValueType.LONG),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.LONG),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(63, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label4,
            new CfLoad(ValueType.LONG, 6),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LE, ValueType.INT, label5),
            new CfLoad(ValueType.LONG, 4),
            new CfGoto(label6),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 6},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.longType)))),
            new CfReturn(ValueType.LONG),
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModLongInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("floorMod")),
                false),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_incrementExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(2147483647, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_incrementExactLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(9223372036854775807L, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.longType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(1, ValueType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.INT, 4),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.INT, 4),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactLong(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        7,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            label1,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.longType),
                    options.itemFactory.createString("numberOfLeadingZeros")),
                false),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-1, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            label2,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.longType),
                    options.itemFactory.createString("numberOfLeadingZeros")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfLoad(ValueType.LONG, 2),
            label3,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.longType),
                    options.itemFactory.createString("numberOfLeadingZeros")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-1, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            label4,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Long;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.longType),
                    options.itemFactory.createString("numberOfLeadingZeros")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfStore(ValueType.INT, 4),
            label5,
            new CfLoad(ValueType.INT, 4),
            new CfConstNumber(65, ValueType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label7),
            label6,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfConstNumber(64, ValueType.INT),
            new CfIfCmp(If.Type.LT, ValueType.INT, label15),
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label8),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label9),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label9,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.EQ, ValueType.INT, label10),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label11),
            label10,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfConstNumber(0, ValueType.INT),
            label11,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(options.itemFactory.intType),
                        FrameType.initialized(options.itemFactory.intType)))),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.INT),
            new CfIf(If.Type.EQ, ValueType.INT, label15),
            label12,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 5),
            label13,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.EQ, ValueType.INT, label14),
            new CfLoad(ValueType.LONG, 5),
            new CfLoad(ValueType.LONG, 0),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Div, NumericType.LONG),
            new CfLoad(ValueType.LONG, 2),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label15),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4, 5},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 5),
            new CfReturn(ValueType.LONG),
            label15,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label16),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactLongInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.longType,
                        options.itemFactory.longType,
                        options.itemFactory.longType),
                    options.itemFactory.createString("multiplyExact")),
                false),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyFull(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyHigh(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        32,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfStore(ValueType.LONG, 6),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 8),
            label3,
            new CfLoad(ValueType.LONG, 2),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfStore(ValueType.LONG, 10),
            label4,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 8),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 12),
            label5,
            new CfLoad(ValueType.LONG, 12),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
            new CfStore(ValueType.LONG, 14),
            label6,
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.LONG, 8),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 16),
            label7,
            new CfLoad(ValueType.LONG, 16),
            new CfLoad(ValueType.LONG, 14),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfStore(ValueType.LONG, 18),
            label8,
            new CfLoad(ValueType.LONG, 18),
            new CfConstNumber(4294967295L, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfStore(ValueType.LONG, 20),
            label9,
            new CfLoad(ValueType.LONG, 18),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfStore(ValueType.LONG, 22),
            label10,
            new CfLoad(ValueType.LONG, 4),
            new CfLoad(ValueType.LONG, 10),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 24),
            label11,
            new CfLoad(ValueType.LONG, 24),
            new CfLoad(ValueType.LONG, 20),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfStore(ValueType.LONG, 26),
            label12,
            new CfLoad(ValueType.LONG, 26),
            new CfConstNumber(32, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Shr, NumericType.LONG),
            new CfStore(ValueType.LONG, 28),
            label13,
            new CfLoad(ValueType.LONG, 6),
            new CfLoad(ValueType.LONG, 10),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.LONG),
            new CfStore(ValueType.LONG, 30),
            label14,
            new CfLoad(ValueType.LONG, 30),
            new CfLoad(ValueType.LONG, 22),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfLoad(ValueType.LONG, 28),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_negateExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(-2147483648, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.intType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfNeg(NumericType.INT),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_negateExactLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfConstNumber(-9223372036854775808L, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.longType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.LONG, 0),
            new CfNeg(NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_nextDownDouble(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.DOUBLE, 0),
            new CfNeg(NumericType.DOUBLE),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.doubleType, options.itemFactory.doubleType),
                    options.itemFactory.createString("nextUp")),
                false),
            new CfNeg(NumericType.DOUBLE),
            new CfReturn(ValueType.DOUBLE),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_nextDownFloat(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.FLOAT, 0),
            new CfNeg(NumericType.FLOAT),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Math;"),
                    options.itemFactory.createProto(
                        options.itemFactory.floatType, options.itemFactory.floatType),
                    options.itemFactory.createString("nextUp")),
                false),
            new CfNeg(NumericType.FLOAT),
            new CfReturn(ValueType.FLOAT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_subtractExactInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfLoad(ValueType.INT, 1),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 2),
            label1,
            new CfLoad(ValueType.LONG, 2),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfStore(ValueType.INT, 4),
            label2,
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.INT, 4),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.NE, ValueType.INT, label4),
            label3,
            new CfLoad(ValueType.INT, 4),
            new CfReturn(ValueType.INT),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_subtractExactLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        6,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.LONG),
            new CfStore(ValueType.LONG, 4),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 2),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label2),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.LONG, 4),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
            new CfConstNumber(0, ValueType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.LT, ValueType.INT, label4),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfConstNumber(0, ValueType.INT),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(options.itemFactory.intType),
                        FrameType.initialized(options.itemFactory.intType)))),
            new CfLogicalBinop(CfLogicalBinop.Opcode.Or, NumericType.INT),
            new CfIf(If.Type.EQ, ValueType.INT, label7),
            label6,
            new CfLoad(ValueType.LONG, 4),
            new CfReturn(ValueType.LONG),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.longType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_toIntExact(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.LONG, 0),
            new CfNumberConversion(NumericType.LONG, NumericType.INT),
            new CfStore(ValueType.INT, 2),
            label1,
            new CfLoad(ValueType.LONG, 0),
            new CfLoad(ValueType.INT, 2),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfCmp(Cmp.Bias.NONE, NumericType.LONG),
            new CfIf(If.Type.EQ, ValueType.INT, label3),
            label2,
            new CfNew(options.itemFactory.createType("Ljava/lang/ArithmeticException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/ArithmeticException;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.longType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromIndexSize(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 2),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfIfCmp(If.Type.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Range [")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(", ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" + ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromToIndex(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.GT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Range [")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(", ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkIndex(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfIf(If.Type.LT, ValueType.INT, label1),
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.LT, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("Index ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstString(options.itemFactory.createString(" out of bounds for length ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 0),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Comparator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Comparator;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.objectType,
                        options.itemFactory.objectType),
                    options.itemFactory.createString("compare")),
                true),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/Comparator;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_deepEquals(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    CfLabel label19 = new CfLabel();
    CfLabel label20 = new CfLabel();
    CfLabel label21 = new CfLabel();
    CfLabel label22 = new CfLabel();
    CfLabel label23 = new CfLabel();
    CfLabel label24 = new CfLabel();
    CfLabel label25 = new CfLabel();
    CfLabel label26 = new CfLabel();
    CfLabel label27 = new CfLabel();
    CfLabel label28 = new CfLabel();
    CfLabel label29 = new CfLabel();
    CfLabel label30 = new CfLabel();
    CfLabel label31 = new CfLabel();
    CfLabel label32 = new CfLabel();
    CfLabel label33 = new CfLabel();
    CfLabel label34 = new CfLabel();
    CfLabel label35 = new CfLabel();
    CfLabel label36 = new CfLabel();
    CfLabel label37 = new CfLabel();
    CfLabel label38 = new CfLabel();
    CfLabel label39 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfReturn(ValueType.INT),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label2),
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.booleanArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label6),
            label3,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.booleanArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label4),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.booleanArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.booleanArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.booleanArrayType,
                        options.itemFactory.booleanArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label4),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.byteArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label10),
            label7,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.byteArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.byteArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.byteArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.byteArrayType,
                        options.itemFactory.byteArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label9),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label9,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label10,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.charArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label14),
            label11,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.charArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label12),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.charArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.charArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.charArrayType,
                        options.itemFactory.charArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label12),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label13),
            label12,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label13,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.doubleArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label18),
            label15,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.doubleArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label16),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.doubleArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.doubleArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.doubleArrayType,
                        options.itemFactory.doubleArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label16),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label17),
            label16,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label17,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label18,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.floatArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label22),
            label19,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.floatArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label20),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.floatArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.floatArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.floatArrayType,
                        options.itemFactory.floatArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label20),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label21),
            label20,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label21,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label22,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.intArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label26),
            label23,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.intArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label24),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.intArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.intArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.intArrayType,
                        options.itemFactory.intArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label24),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label25),
            label24,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label25,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label26,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.longArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label30),
            label27,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.longArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label28),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.longArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.longArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.longArrayType,
                        options.itemFactory.longArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label28),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label29),
            label28,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label29,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label30,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.shortArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label34),
            label31,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.shortArrayType),
            new CfIf(If.Type.EQ, ValueType.INT, label32),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.shortArrayType),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.shortArrayType),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.shortArrayType,
                        options.itemFactory.shortArrayType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label32),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label33),
            label32,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label33,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label34,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceOf(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfIf(If.Type.EQ, ValueType.INT, label38),
            label35,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInstanceOf(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfIf(If.Type.EQ, ValueType.INT, label36),
            new CfLoad(ValueType.OBJECT, 0),
            new CfCheckCast(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfCheckCast(options.itemFactory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Arrays;"),
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType,
                        options.itemFactory.createType("[Ljava/lang/Object;"),
                        options.itemFactory.createType("[Ljava/lang/Object;")),
                    options.itemFactory.createString("deepEquals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label36),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label37),
            label36,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label37,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label38,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("equals")),
                false),
            new CfReturn(ValueType.INT),
            label39),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_equals(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfIfCmp(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.objectType),
                    options.itemFactory.createString("equals")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_hashCode(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(0, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("hashCode")),
                false),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_isNull(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_nonNull(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullElse(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(options.itemFactory.createString("defaultObj")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.stringType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfReturn(ValueType.OBJECT),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullElseGet(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.EQ, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/Supplier;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(options.itemFactory.createString("supplier")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.stringType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfCheckCast(options.itemFactory.createType("Ljava/util/function/Supplier;")),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/Supplier;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("get")),
                true),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfConstString(options.itemFactory.createString("supplier.get()")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType,
                        options.itemFactory.objectType,
                        options.itemFactory.stringType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullMessage(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toString(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstString(options.itemFactory.createString("null")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.objectType,
                        options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toStringDefault(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfLoad(ValueType.OBJECT, 1),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.stringType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.objectType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.objectType),
                      FrameType.initialized(options.itemFactory.stringType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.stringType)))),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElse(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("get")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/Consumer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.objectType),
                    options.itemFactory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/Consumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Runnable;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/Consumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseDouble(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalDouble;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalDouble;"),
                    options.itemFactory.createProto(options.itemFactory.doubleType),
                    options.itemFactory.createString("getAsDouble")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/DoubleConsumer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.doubleType),
                    options.itemFactory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalDouble;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/DoubleConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Runnable;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalDouble;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/DoubleConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseInt(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalInt;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalInt;"),
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("getAsInt")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/IntConsumer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalInt;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/IntConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Runnable;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalInt;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/IntConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseLong(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalLong;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalLong;"),
                    options.itemFactory.createProto(options.itemFactory.longType),
                    options.itemFactory.createString("getAsLong")),
                false),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/LongConsumer;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.longType),
                    options.itemFactory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalLong;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/LongConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Runnable;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalLong;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/LongConsumer;")),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Runnable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfReturnVoid(),
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmpty(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyDouble(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalDouble;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalDouble;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalDouble;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalInt;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalInt;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalInt;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalLong;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label1),
            new CfConstNumber(1, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalLong;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(0, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalLong;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initialized(options.itemFactory.intType)))),
            new CfReturn(ValueType.INT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_or(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label3),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label3,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;")),
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/function/Supplier;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/function/Supplier;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("get")),
                true),
            new CfCheckCast(options.itemFactory.createType("Ljava/util/Optional;")),
            new CfStore(ValueType.OBJECT, 2),
            label4,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Objects;"),
                    options.itemFactory.createProto(
                        options.itemFactory.objectType, options.itemFactory.objectType),
                    options.itemFactory.createString("requireNonNull")),
                false),
            new CfCheckCast(options.itemFactory.createType("Ljava/util/Optional;")),
            new CfReturn(ValueType.OBJECT),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_stream(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Optional;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("get")),
                false),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/Stream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/Stream;"),
                        options.itemFactory.objectType),
                    options.itemFactory.createString("of")),
                true),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Optional;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/Stream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/Stream;")),
                    options.itemFactory.createString("empty")),
                true),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamDouble(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalDouble;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalDouble;"),
                    options.itemFactory.createProto(options.itemFactory.doubleType),
                    options.itemFactory.createString("getAsDouble")),
                false),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/DoubleStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/DoubleStream;"),
                        options.itemFactory.doubleType),
                    options.itemFactory.createString("of")),
                true),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalDouble;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/DoubleStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/DoubleStream;")),
                    options.itemFactory.createString("empty")),
                true),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalInt;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalInt;"),
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("getAsInt")),
                false),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/IntStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/IntStream;"),
                        options.itemFactory.intType),
                    options.itemFactory.createString("of")),
                true),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalInt;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/IntStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/IntStream;")),
                    options.itemFactory.createString("empty")),
                true),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalLong;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("isPresent")),
                false),
            new CfIf(If.Type.EQ, ValueType.INT, label2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/OptionalLong;"),
                    options.itemFactory.createProto(options.itemFactory.longType),
                    options.itemFactory.createString("getAsLong")),
                false),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/LongStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/LongStream;"),
                        options.itemFactory.longType),
                    options.itemFactory.createString("of")),
                true),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initialized(
                          options.itemFactory.createType("Ljava/util/OptionalLong;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/LongStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/LongStream;")),
                    options.itemFactory.createString("empty")),
                true),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_compare(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_compareUnsigned(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(65535, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(65535, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_toUnsignedInt(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfConstNumber(65535, ValueType.INT),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.INT),
            new CfReturn(ValueType.INT),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_toUnsignedLong(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 0),
            new CfNumberConversion(NumericType.INT, NumericType.LONG),
            new CfConstNumber(65535, ValueType.LONG),
            new CfLogicalBinop(CfLogicalBinop.Opcode.And, NumericType.LONG),
            new CfReturn(ValueType.LONG),
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StreamMethods_ofNullable(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/Stream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/Stream;")),
                    options.itemFactory.createString("empty")),
                true),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/stream/Stream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/stream/Stream;"),
                        options.itemFactory.objectType),
                    options.itemFactory.createString("of")),
                true),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initialized(
                            options.itemFactory.createType("Ljava/util/stream/Stream;"))))),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_isBlank(InternalOptions options, DexMethod method) {
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
        2,
        4,
        ImmutableList.of(
            label0,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.GE, ValueType.INT, label8),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("codePointAt")),
                false),
            new CfStore(ValueType.INT, 3),
            label4,
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.intType),
                    options.itemFactory.createString("isWhitespace")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label6),
            label5,
            new CfConstNumber(0, ValueType.INT),
            new CfReturn(ValueType.INT),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("charCount")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfStore(ValueType.INT, 1),
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initialized(options.itemFactory.stringType)}),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstNumber(1, ValueType.INT),
            new CfReturn(ValueType.INT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinArray(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("delimiter")),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/lang/CharSequence;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIf(If.Type.LE, ValueType.INT, label9),
            label3,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstNumber(0, ValueType.INT),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label4,
            new CfConstNumber(1, ValueType.INT),
            new CfStore(ValueType.INT, 3),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/lang/CharSequence;")),
                      FrameType.initialized(options.itemFactory.stringBuilderType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 3),
            new CfLoad(ValueType.OBJECT, 1),
            new CfArrayLength(),
            new CfIfCmp(If.Type.GE, ValueType.INT, label9),
            label6,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label7,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.INT, 3),
            new CfArrayLoad(MemberType.OBJECT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label8,
            new CfIinc(3, 1),
            new CfGoto(label5),
            label9,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(
                          options.itemFactory.createType("[Ljava/lang/CharSequence;")),
                      FrameType.initialized(options.itemFactory.stringBuilderType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label10),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinIterable(InternalOptions options, DexMethod method) {
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
        3,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(If.Type.NE, ValueType.OBJECT, label1),
            new CfNew(options.itemFactory.createType("Ljava/lang/NullPointerException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(options.itemFactory.createString("delimiter")),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/NullPointerException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Iterable;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label2,
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/Iterable;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/util/Iterator;")),
                    options.itemFactory.createString("iterator")),
                true),
            new CfStore(ValueType.OBJECT, 3),
            label3,
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            label4,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("next")),
                true),
            new CfCheckCast(options.itemFactory.charSequenceType),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Iterable;")),
                      FrameType.initialized(options.itemFactory.stringBuilderType),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Iterator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.booleanType),
                    options.itemFactory.createString("hasNext")),
                true),
            new CfIf(If.Type.EQ, ValueType.INT, label8),
            label6,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label7,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                185,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/util/Iterator;"),
                    options.itemFactory.createProto(options.itemFactory.objectType),
                    options.itemFactory.createString("next")),
                true),
            new CfCheckCast(options.itemFactory.charSequenceType),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType,
                        options.itemFactory.charSequenceType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            new CfGoto(label5),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.charSequenceType),
                      FrameType.initialized(options.itemFactory.createType("Ljava/lang/Iterable;")),
                      FrameType.initialized(options.itemFactory.stringBuilderType),
                      FrameType.initialized(options.itemFactory.createType("Ljava/util/Iterator;"))
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_repeat(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        5,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.GE, ValueType.INT, label2),
            label1,
            new CfNew(options.itemFactory.createType("Ljava/lang/IllegalArgumentException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstString(options.itemFactory.createString("count is negative: ")),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.intType),
                    options.itemFactory.createString("append")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/lang/IllegalArgumentException;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfThrow(),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 2),
            label3,
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.EQ, ValueType.INT, label4),
            new CfLoad(ValueType.INT, 2),
            new CfIf(If.Type.NE, ValueType.INT, label5),
            label4,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfConstString(options.itemFactory.createString("")),
            new CfReturn(ValueType.OBJECT),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfConstNumber(1, ValueType.INT),
            new CfIfCmp(If.Type.NE, ValueType.INT, label7),
            label6,
            new CfLoad(ValueType.OBJECT, 0),
            new CfReturn(ValueType.OBJECT),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 1),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Mul, NumericType.INT),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.intType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 3),
            label8,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label9,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.stringBuilderType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.GE, ValueType.INT, label12),
            label10,
            new CfLoad(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label11,
            new CfIinc(4, 1),
            new CfGoto(label9),
            label12,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.stringBuilderType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 3),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfReturn(ValueType.OBJECT),
            label13),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_strip(InternalOptions options, DexMethod method) {
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
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.GE, ValueType.INT, label8),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("codePointAt")),
                false),
            new CfStore(ValueType.INT, 3),
            label4,
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.intType),
                    options.itemFactory.createString("isWhitespace")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label6),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("charCount")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfStore(ValueType.INT, 1),
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 1),
            new CfIfCmp(If.Type.LE, ValueType.INT, label14),
            label9,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.charSequenceType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("codePointBefore")),
                false),
            new CfStore(ValueType.INT, 3),
            label10,
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.intType),
                    options.itemFactory.createString("isWhitespace")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label12),
            label11,
            new CfGoto(label14),
            label12,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 2),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("charCount")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfStore(ValueType.INT, 2),
            label13,
            new CfGoto(label8),
            label14,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("substring")),
                false),
            new CfReturn(ValueType.OBJECT),
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_stripLeading(InternalOptions options, DexMethod method) {
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
        3,
        4,
        ImmutableList.of(
            label0,
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 1),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 2),
            label2,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfIfCmp(If.Type.GE, ValueType.INT, label8),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("codePointAt")),
                false),
            new CfStore(ValueType.INT, 3),
            label4,
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.intType),
                    options.itemFactory.createString("isWhitespace")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label6),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 3),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("charCount")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, NumericType.INT),
            new CfStore(ValueType.INT, 1),
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("substring")),
                false),
            new CfReturn(ValueType.OBJECT),
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_stripTrailing(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        3,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(options.itemFactory.intType),
                    options.itemFactory.createString("length")),
                false),
            new CfStore(ValueType.INT, 1),
            label1,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfIf(If.Type.LE, ValueType.INT, label7),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.charSequenceType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("codePointBefore")),
                false),
            new CfStore(ValueType.INT, 2),
            label3,
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.booleanType, options.itemFactory.intType),
                    options.itemFactory.createString("isWhitespace")),
                false),
            new CfIf(If.Type.NE, ValueType.INT, label5),
            label4,
            new CfGoto(label7),
            label5,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.INT, 1),
            new CfLoad(ValueType.INT, 2),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.boxedCharType,
                    options.itemFactory.createProto(
                        options.itemFactory.intType, options.itemFactory.intType),
                    options.itemFactory.createString("charCount")),
                false),
            new CfArithmeticBinop(CfArithmeticBinop.Opcode.Sub, NumericType.INT),
            new CfStore(ValueType.INT, 1),
            label6,
            new CfGoto(label1),
            label7,
            new CfFrame(
                new Int2ReferenceAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initialized(options.itemFactory.stringType),
                      FrameType.initialized(options.itemFactory.intType)
                    }),
                new ArrayDeque<>(Arrays.asList())),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(0, ValueType.INT),
            new CfLoad(ValueType.INT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringType,
                        options.itemFactory.intType,
                        options.itemFactory.intType),
                    options.itemFactory.createString("substring")),
                false),
            new CfReturn(ValueType.OBJECT),
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
