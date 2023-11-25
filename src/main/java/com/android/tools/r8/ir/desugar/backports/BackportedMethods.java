// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
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
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
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
    factory.createSynthesizedType("Ljava/lang/OutOfMemoryError;");
    factory.createSynthesizedType("Ljava/lang/Runnable;");
    factory.createSynthesizedType("Ljava/lang/RuntimeException;");
    factory.createSynthesizedType("Ljava/lang/SecurityException;");
    factory.createSynthesizedType("Ljava/lang/reflect/Constructor;");
    factory.createSynthesizedType("Ljava/lang/reflect/InvocationTargetException;");
    factory.createSynthesizedType("Ljava/lang/reflect/Method;");
    factory.createSynthesizedType("Ljava/math/BigDecimal;");
    factory.createSynthesizedType("Ljava/math/BigInteger;");
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
    factory.createSynthesizedType("Ljava/util/concurrent/atomic/AtomicReference;");
    factory.createSynthesizedType("Ljava/util/concurrent/atomic/AtomicReferenceArray;");
    factory.createSynthesizedType("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;");
    factory.createSynthesizedType("Ljava/util/function/Consumer;");
    factory.createSynthesizedType("Ljava/util/function/DoubleConsumer;");
    factory.createSynthesizedType("Ljava/util/function/IntConsumer;");
    factory.createSynthesizedType("Ljava/util/function/LongConsumer;");
    factory.createSynthesizedType("Ljava/util/function/Predicate;");
    factory.createSynthesizedType("Ljava/util/function/Supplier;");
    factory.createSynthesizedType("Ljava/util/stream/DoubleStream;");
    factory.createSynthesizedType("Ljava/util/stream/IntStream;");
    factory.createSynthesizedType("Ljava/util/stream/LongStream;");
    factory.createSynthesizedType("Ljava/util/stream/Stream;");
    factory.createSynthesizedType("Lsun/misc/Unsafe;");
    factory.createSynthesizedType("[Ljava/lang/CharSequence;");
    factory.createSynthesizedType("[Ljava/lang/Class;");
    factory.createSynthesizedType("[Ljava/lang/Object;");
    factory.createSynthesizedType("[Ljava/lang/Throwable;");
    factory.createSynthesizedType("[Ljava/util/Map$Entry;");
  }

  public static CfCode AssertionErrorMethods_createAssertionError(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        3,
        ImmutableList.of(
            label0,
            new CfConstClass(factory.createType("Ljava/lang/AssertionError;")),
            CfConstNumber.ICONST_2,
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            new CfConstClass(factory.stringType),
            CfArrayStore.forType(MemberType.OBJECT),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_1,
            new CfConstClass(factory.throwableType),
            CfArrayStore.forType(MemberType.OBJECT),
            label1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Constructor;"),
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredConstructor")),
                false),
            CfStore.ASTORE_2,
            label2,
            CfLoad.ALOAD_2,
            CfConstNumber.ICONST_2,
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            CfLoad.ALOAD_0,
            CfArrayStore.forType(MemberType.OBJECT),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_1,
            CfLoad.ALOAD_1,
            CfArrayStore.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Constructor;"),
                    factory.createProto(
                        factory.objectType, factory.createType("[Ljava/lang/Object;")),
                    factory.createString("newInstance")),
                false),
            new CfCheckCast(factory.createType("Ljava/lang/AssertionError;")),
            label3,
            CfReturn.ARETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            CfStore.ASTORE_2,
            label5,
            new CfNew(factory.createType("Ljava/lang/AssertionError;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/AssertionError;"),
                    factory.createProto(factory.voidType, factory.objectType),
                    factory.createString("<init>")),
                false),
            CfReturn.ARETURN,
            label6),
        ImmutableList.of(
            new CfTryCatch(
                label0,
                label3,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label4))),
        ImmutableList.of());
  }

  public static CfCode AtomicReferenceArrayMethods_compareAndSet(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceArray;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceArray;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.intType,
                        factory.objectType,
                        factory.objectType),
                    factory.createString("compareAndSet")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceArray;")),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceArray;"),
                    factory.createProto(factory.objectType, factory.intType),
                    factory.createString("get")),
                false),
            CfLoad.ALOAD_2,
            new CfIfCmp(IfType.EQ, ValueType.OBJECT, label0),
            label3,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode AtomicReferenceFieldUpdaterMethods_compareAndSet(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.objectType,
                        factory.objectType,
                        factory.objectType),
                    factory.createString("compareAndSet")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("get")),
                false),
            CfLoad.ALOAD_2,
            new CfIfCmp(IfType.EQ, ValueType.OBJECT, label0),
            label3,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode AtomicReferenceMethods_compareAndSet(
      DexItemFactory factory, DexMethod method) {
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
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/concurrent/atomic/AtomicReference;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReference;"),
                    factory.createProto(
                        factory.booleanType, factory.objectType, factory.objectType),
                    factory.createString("compareAndSet")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/concurrent/atomic/AtomicReference;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/concurrent/atomic/AtomicReference;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                false),
            CfLoad.ALOAD_1,
            new CfIfCmp(IfType.EQ, ValueType.OBJECT, label0),
            label3,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BigDecimalMethods_stripTrailingZeros(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/math/BigDecimal;"),
                    factory.createProto(factory.intType),
                    factory.createString("signum")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/math/BigDecimal;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Ljava/math/BigInteger;"),
                    factory.createType("Ljava/math/BigInteger;"),
                    factory.createString("ZERO"))),
            new CfConstNumber(0, ValueType.INT),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/math/BigDecimal;"),
                    factory.createProto(
                        factory.voidType,
                        factory.createType("Ljava/math/BigInteger;"),
                        factory.intType),
                    factory.createString("<init>")),
                false),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/math/BigDecimal;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/math/BigDecimal;"),
                    factory.createProto(factory.createType("Ljava/math/BigDecimal;")),
                    factory.createString("stripTrailingZeros")),
                false),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_compare(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_0,
            new CfGoto(label3),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            CfLoad.ILOAD_0,
            new CfIf(IfType.EQ, ValueType.INT, label2),
            CfConstNumber.ICONST_1,
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            CfConstNumber.ICONST_M1,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode BooleanMethods_hashCode(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            new CfIf(IfType.EQ, ValueType.INT, label1),
            CfConstNumber.constNumber(1231, ValueType.INT),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            CfConstNumber.constNumber(1237, ValueType.INT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_compare(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_compareUnsigned(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(255, ValueType.INT),
            CfLogicalBinop.IAND,
            CfLoad.ILOAD_1,
            CfConstNumber.constNumber(255, ValueType.INT),
            CfLogicalBinop.IAND,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_toUnsignedInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(255, ValueType.INT),
            CfLogicalBinop.IAND,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ByteMethods_toUnsignedLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(255, ValueType.LONG),
            CfLogicalBinop.LAND,
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharSequenceMethods_compare(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                true),
             CfStore.ISTORE_2,
            label1,
            CfLoad.ALOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                true),
             CfStore.ISTORE_3,
            label2,
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfIfCmp(IfType.NE, ValueType.OBJECT, label4),
            label3,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_4,
            label5,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.intType, factory.intType, factory.intType),
                    factory.createString("min")),
                false),
             CfStore.ISTORE_5,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfLoad.ILOAD_5,
            new CfIfCmp(IfType.GE, ValueType.INT, label12),
            label7,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_4,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
             CfStore.ISTORE_6,
            label8,
            CfLoad.ALOAD_1,
            CfLoad.ILOAD_4,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
             CfStore.ISTORE_7,
            label9,
            CfLoad.ILOAD_6,
            CfLoad.ILOAD_7,
            new CfIfCmp(IfType.EQ, ValueType.INT, label11),
            label10,
            CfLoad.ILOAD_6,
            CfLoad.ILOAD_7,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfIinc(4, 1),
            new CfGoto(label6),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_3,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label13),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharacterMethods_compare(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CharacterMethods_toStringCodepoint(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfNew(factory.stringType),
            CfStackInstruction.DUP,
            CfLoad.ILOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.charArrayType, factory.intType),
                    factory.createString("toChars")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.voidType, factory.charArrayType),
                    factory.createString("<init>")),
                false),
            CfReturn.ARETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CloseResourceMethod_closeResourceImpl(
      DexItemFactory factory, DexMethod method) {
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
        6,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.autoCloseableType),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.autoCloseableType),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.autoCloseableType,
                    factory.createProto(factory.voidType),
                    factory.createString("close")),
                true),
            new CfGoto(label11),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfConstString(factory.createString("close")),
            CfConstNumber.ICONST_0,
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getMethod")),
                false),
            CfStore.ASTORE_2,
            label3,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_1,
            CfConstNumber.ICONST_0,
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            CfStackInstruction.POP,
            label4,
            new CfGoto(label11),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            CfStore.ASTORE_2,
            label6,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" does not have a close() method.")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            CfLoad.ALOAD_2,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(
                        factory.voidType, factory.stringType, factory.throwableType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            CfStore.ASTORE_2,
            label8,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Fail to call close() on ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.classType),
                    factory.createString("getClass")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            CfLoad.ALOAD_2,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(
                        factory.voidType, factory.stringType, factory.throwableType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/reflect/InvocationTargetException;"))))),
            CfStore.ASTORE_2,
            label10,
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/InvocationTargetException;"),
                    factory.createProto(factory.throwableType),
                    factory.createString("getCause")),
                false),
            CfThrow.INSTANCE,
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfGoto(label20),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            CfStore.ASTORE_2,
            label13,
            CfLoad.ALOAD_0,
            new CfIf(IfType.EQ, ValueType.OBJECT, label19),
            label14,
            new CfConstClass(factory.throwableType),
            new CfConstString(factory.createString("addSuppressed")),
            CfConstNumber.ICONST_1,
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            new CfConstClass(factory.throwableType),
            CfArrayStore.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredMethod")),
                false),
             CfStore.ASTORE_3,
            label15,
            CfLoad.ALOAD_3,
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_1,
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            CfLoad.ALOAD_2,
            CfArrayStore.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            CfStackInstruction.POP,
            label16,
            new CfGoto(label18),
            label17,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
             CfStore.ASTORE_3,
            label18,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    })),
            CfLoad.ALOAD_0,
            CfThrow.INSTANCE,
            label19,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    })),
            CfLoad.ALOAD_2,
            CfThrow.INSTANCE,
            label20,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfReturnVoid.INSTANCE,
            label21),
        ImmutableList.of(
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchMethodException;")),
                ImmutableList.of(label5)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(factory.createType("Ljava/lang/SecurityException;")),
                ImmutableList.of(label5)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(factory.createType("Ljava/lang/IllegalAccessException;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(factory.createType("Ljava/lang/IllegalArgumentException;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(factory.createType("Ljava/lang/ExceptionInInitializerError;")),
                ImmutableList.of(label7)),
            new CfTryCatch(
                label2,
                label4,
                ImmutableList.of(
                    factory.createType("Ljava/lang/reflect/InvocationTargetException;")),
                ImmutableList.of(label9)),
            new CfTryCatch(
                label0,
                label11,
                ImmutableList.of(factory.throwableType),
                ImmutableList.of(label12)),
            new CfTryCatch(
                label14,
                label16,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label17))),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_listOfArray(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/ArrayList;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            CfArrayLength.INSTANCE,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/ArrayList;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            CfStore.ASTORE_2,
            CfLoad.ALOAD_2,
            CfArrayLength.INSTANCE,
             CfStore.ISTORE_3,
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_4,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/ArrayList;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfLoad.ILOAD_3,
            new CfIfCmp(IfType.GE, ValueType.INT, label5),
            CfLoad.ALOAD_2,
            CfLoad.ILOAD_4,
            CfArrayLoad.forType(MemberType.OBJECT),
             CfStore.ASTORE_5,
            label3,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_5,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/ArrayList;"),
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("add")),
                false),
            CfStackInstruction.POP,
            label4,
            new CfIinc(4, 1),
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/ArrayList;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/List;"),
                        factory.createType("Ljava/util/List;")),
                    factory.createString("unmodifiableList")),
                false),
            CfReturn.ARETURN,
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_mapEntry(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/AbstractMap$SimpleImmutableEntry;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            label1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            CfLoad.ALOAD_1,
            label2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/AbstractMap$SimpleImmutableEntry;"),
                    factory.createProto(factory.voidType, factory.objectType, factory.objectType),
                    factory.createString("<init>")),
                false),
            label3,
            CfReturn.ARETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_mapOfEntries(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/HashMap;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            CfArrayLength.INSTANCE,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/HashMap;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            CfStore.ASTORE_2,
            CfLoad.ALOAD_2,
            CfArrayLength.INSTANCE,
             CfStore.ISTORE_3,
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_4,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashMap;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfLoad.ILOAD_3,
            new CfIfCmp(IfType.GE, ValueType.INT, label8),
            CfLoad.ALOAD_2,
            CfLoad.ILOAD_4,
            CfArrayLoad.forType(MemberType.OBJECT),
             CfStore.ASTORE_5,
            label3,
            CfLoad.ALOAD_5,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map$Entry;"),
                    factory.createProto(factory.objectType),
                    factory.createString("getKey")),
                true),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
             CfStore.ASTORE_6,
            label4,
            CfLoad.ALOAD_5,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map$Entry;"),
                    factory.createProto(factory.objectType),
                    factory.createString("getValue")),
                true),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
             CfStore.ASTORE_7,
            label5,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_6,
            CfLoad.ALOAD_7,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/HashMap;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.objectType),
                    factory.createString("put")),
                false),
            new CfIf(IfType.EQ, ValueType.OBJECT, label7),
            label6,
            new CfNew(factory.createType("Ljava/lang/IllegalArgumentException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("duplicate key: ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_6,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IllegalArgumentException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashMap;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfIinc(4, 1),
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/util/Map$Entry;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashMap;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/Map;"),
                        factory.createType("Ljava/util/Map;")),
                    factory.createString("unmodifiableMap")),
                false),
            CfReturn.ARETURN,
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionMethods_setOfArray(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/HashSet;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            CfArrayLength.INSTANCE,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/HashSet;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            CfStore.ASTORE_2,
            CfLoad.ALOAD_2,
            CfArrayLength.INSTANCE,
             CfStore.ISTORE_3,
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_4,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashSet;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfLoad.ILOAD_3,
            new CfIfCmp(IfType.GE, ValueType.INT, label6),
            CfLoad.ALOAD_2,
            CfLoad.ILOAD_4,
            CfArrayLoad.forType(MemberType.OBJECT),
             CfStore.ASTORE_5,
            label3,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_5,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/HashSet;"),
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("add")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label5),
            label4,
            new CfNew(factory.createType("Ljava/lang/IllegalArgumentException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("duplicate element: ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_5,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.objectType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IllegalArgumentException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashSet;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfIinc(4, 1),
            new CfGoto(label2),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/Object;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashSet;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/Set;"),
                        factory.createType("Ljava/util/Set;")),
                    factory.createString("unmodifiableSet")),
                false),
            CfReturn.ARETURN,
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfList(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/ArrayList;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Collection;"),
                    factory.createProto(factory.intType),
                    factory.createString("size")),
                true),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/ArrayList;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Collection;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                true),
            CfStore.ASTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Collection;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/ArrayList;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label5),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
             CfStore.ASTORE_3,
            label3,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/ArrayList;"),
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("add")),
                false),
            CfStackInstruction.POP,
            label4,
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Collection;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/ArrayList;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/List;"),
                        factory.createType("Ljava/util/List;")),
                    factory.createString("unmodifiableList")),
                false),
            CfReturn.ARETURN,
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfMap(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/HashMap;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map;"),
                    factory.createProto(factory.intType),
                    factory.createString("size")),
                true),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/HashMap;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map;"),
                    factory.createProto(factory.createType("Ljava/util/Set;")),
                    factory.createString("entrySet")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Set;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                true),
            CfStore.ASTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.createType("Ljava/util/Map;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashMap;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
            new CfCheckCast(factory.createType("Ljava/util/Map$Entry;")),
             CfStore.ASTORE_3,
            label3,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_3,
            label4,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map$Entry;"),
                    factory.createProto(factory.objectType),
                    factory.createString("getKey")),
                true),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            CfLoad.ALOAD_3,
            label5,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Map$Entry;"),
                    factory.createProto(factory.objectType),
                    factory.createString("getValue")),
                true),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            label6,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/HashMap;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.objectType),
                    factory.createString("put")),
                false),
            CfStackInstruction.POP,
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.createType("Ljava/util/Map;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashMap;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/Map;"),
                        factory.createType("Ljava/util/Map;")),
                    factory.createString("unmodifiableMap")),
                false),
            CfReturn.ARETURN,
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_copyOfSet(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/util/HashSet;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Collection;"),
                    factory.createProto(factory.intType),
                    factory.createString("size")),
                true),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/HashSet;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Collection;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                true),
            CfStore.ASTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Collection;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashSet;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label5),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
             CfStore.ASTORE_3,
            label3,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/HashSet;"),
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("add")),
                false),
            CfStackInstruction.POP,
            label4,
            new CfGoto(label2),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Collection;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/HashSet;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/Set;"),
                        factory.createType("Ljava/util/Set;")),
                    factory.createString("unmodifiableSet")),
                false),
            CfReturn.ARETURN,
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyEnumeration(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(factory.createType("Ljava/util/List;")),
                    factory.createString("emptyList")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(
                        factory.createType("Ljava/util/Enumeration;"),
                        factory.createType("Ljava/util/Collection;")),
                    factory.createString("enumeration")),
                false),
            CfReturn.ARETURN),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyIterator(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(factory.createType("Ljava/util/List;")),
                    factory.createString("emptyList")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/List;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                true),
            CfReturn.ARETURN),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode CollectionsMethods_emptyListIterator(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Collections;"),
                    factory.createProto(factory.createType("Ljava/util/List;")),
                    factory.createString("emptyList")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/List;"),
                    factory.createProto(factory.createType("Ljava/util/ListIterator;")),
                    factory.createString("listIterator")),
                true),
            CfReturn.ARETURN),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DoubleMethods_hashCode(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            CfLoad.DLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Double;"),
                    factory.createProto(factory.longType, factory.doubleType),
                    factory.createString("doubleToLongBits")),
                false),
             CfStore.LSTORE_2,
            label1,
            CfLoad.LLOAD_2,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LUSHR,
            CfLogicalBinop.LXOR,
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode DoubleMethods_isFinite(DexItemFactory factory, DexMethod method) {
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
            CfLoad.DLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Double;"),
                    factory.createProto(factory.booleanType, factory.doubleType),
                    factory.createString("isInfinite")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfLoad.DLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Double;"),
                    factory.createProto(factory.booleanType, factory.doubleType),
                    factory.createString("isNaN")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.doubleType(), FrameType.doubleHighType()})),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.doubleType(), FrameType.doubleHighType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode FloatMethods_isFinite(DexItemFactory factory, DexMethod method) {
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
            CfLoad.FLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Float;"),
                    factory.createProto(factory.booleanType, factory.floatType),
                    factory.createString("isInfinite")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfLoad.FLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Float;"),
                    factory.createProto(factory.booleanType, factory.floatType),
                    factory.createString("isNaN")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.floatType()})),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.floatType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_compare(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_0,
            new CfGoto(label3),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.GE, ValueType.INT, label2),
            CfConstNumber.ICONST_M1,
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            CfConstNumber.ICONST_1,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_compareUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(-2147483648, ValueType.INT),
            CfLogicalBinop.IXOR,
             CfStore.ISTORE_2,
            label1,
            CfLoad.ILOAD_1,
            CfConstNumber.constNumber(-2147483648, ValueType.INT),
            CfLogicalBinop.IXOR,
             CfStore.ISTORE_3,
            label2,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.intType, factory.intType),
                    factory.createString("compare")),
                false),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_divideUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_2,
            label1,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfLoad.LLOAD_4,
            CfArithmeticBinop.LDIV,
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseIntSubsequenceWithRadix(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charSequenceType, factory.intType, factory.intType),
                    factory.createString("subSequence")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.stringType, factory.intType),
                    factory.createString("parseInt")),
                false),
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseIntSubsequenceWithRadixDalvik(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            CfConstNumber.ICONST_2,
            new CfIfCmp(IfType.LT, ValueType.INT, label4),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            label1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfConstNumber.constNumber(43, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label4),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.IADD,
            label2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.charType, factory.intType),
                    factory.createString("digit")),
                false),
            new CfIf(IfType.LT, ValueType.INT, label4),
            label3,
            new CfIinc(1, 1),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charSequenceType, factory.intType, factory.intType),
                    factory.createString("subSequence")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.stringType, factory.intType),
                    factory.createString("parseInt")),
                false),
            CfReturn.IRETURN,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseUnsignedInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfConstNumber.constNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.stringType, factory.intType),
                    factory.createString("parseUnsignedInt")),
                false),
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseUnsignedIntSubsequenceWithRadix(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charSequenceType, factory.intType, factory.intType),
                    factory.createString("subSequence")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.stringType, factory.intType),
                    factory.createString("parseUnsignedInt")),
                false),
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_parseUnsignedIntWithRadix(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
            CfConstNumber.ICONST_1,
            new CfIfCmp(IfType.LE, ValueType.INT, label2),
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                false),
            CfConstNumber.constNumber(43, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType),
                    factory.createString("substring")),
                false),
            CfStore.ASTORE_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType), FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.stringType, factory.intType),
                    factory.createString("parseLong")),
                false),
             CfStore.LSTORE_2,
            label3,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
            CfLoad.LLOAD_2,
            CfCmp.LCMP,
            new CfIf(IfType.EQ, ValueType.INT, label5),
            label4,
            new CfNew(factory.createType("Ljava/lang/NumberFormatException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Input ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" in base ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" is not in the range of an unsigned integer")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NumberFormatException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_2,
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_remainderUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_2,
            label1,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfLoad.LLOAD_4,
            CfArithmeticBinop.LREM,
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedString(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.stringType, factory.intType, factory.intType),
                    factory.createString("toUnsignedString")),
                false),
            CfReturn.ARETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode IntegerMethods_toUnsignedStringWithRadix(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_2,
            label1,
            CfLoad.LLOAD_2,
            CfLoad.ILOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.stringType, factory.longType, factory.intType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_compareUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(6),
            label2,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(6),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.intType, factory.longType, factory.longType),
                    factory.createString("compare")),
                false),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_divideUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_2,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label6),
            label1,
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.LSTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(6),
            label3,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(6),
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label5),
            label4,
            CfConstNumber.LCONST_0,
            CfReturn.LRETURN,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfConstNumber.LCONST_1,
            CfReturn.LRETURN,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label8),
            label7,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LDIV,
            CfReturn.LRETURN,
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.LUSHR,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LDIV,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.LSHL,
             CfStore.LSTORE_4,
            label9,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_4,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LMUL,
            CfArithmeticBinop.LSUB,
             CfStore.storeLong(6),
            label10,
            CfLoad.loadLong(6),
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(8),
            label11,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(10),
            label12,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(8),
            CfLoad.loadLong(10),
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label13),
            CfConstNumber.ICONST_1,
            new CfGoto(label14),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType()))),
            CfConstNumber.ICONST_0,
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType(), FrameType.intType()))),
            CfNumberConversion.I2L,
            CfArithmeticBinop.LADD,
            CfReturn.LRETURN,
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_hashCode(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        2,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LUSHR,
            CfLogicalBinop.LXOR,
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseLongSubsequenceWithRadix(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charSequenceType, factory.intType, factory.intType),
                    factory.createString("subSequence")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.stringType, factory.intType),
                    factory.createString("parseLong")),
                false),
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseLongSubsequenceWithRadixDalvik(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            CfConstNumber.ICONST_2,
            new CfIfCmp(IfType.LT, ValueType.INT, label4),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            label1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfConstNumber.constNumber(43, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label4),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.IADD,
            label2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.charType, factory.intType),
                    factory.createString("digit")),
                false),
            new CfIf(IfType.LT, ValueType.INT, label4),
            label3,
            new CfIinc(1, 1),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charSequenceType, factory.intType, factory.intType),
                    factory.createString("subSequence")),
                true),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.stringType, factory.intType),
                    factory.createString("parseLong")),
                false),
            CfReturn.LRETURN,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseUnsignedLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfConstNumber.constNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.stringType, factory.intType),
                    factory.createString("parseUnsignedLong")),
                false),
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseUnsignedLongSubsequenceWithRadix(
      DexItemFactory factory, DexMethod method) {
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
        12,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
             CfStore.ISTORE_4,
            label1,
            CfLoad.ILOAD_4,
            new CfIf(IfType.NE, ValueType.INT, label3),
            label2,
            new CfNew(factory.createType("Ljava/lang/NumberFormatException;")),
            CfStackInstruction.DUP,
            new CfConstString(factory.createString("empty string")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NumberFormatException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_3,
            CfConstNumber.ICONST_2,
            new CfIfCmp(IfType.LT, ValueType.INT, label4),
            CfLoad.ILOAD_3,
            CfConstNumber.constNumber(36, ValueType.INT),
            new CfIfCmp(IfType.LE, ValueType.INT, label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/NumberFormatException;")),
            CfStackInstruction.DUP,
            new CfConstString(factory.createString("illegal radix: ")),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType),
                    factory.createString("valueOf")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.stringType),
                    factory.createString("concat")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NumberFormatException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfConstNumber.constNumber(-1, ValueType.LONG),
            CfLoad.ILOAD_3,
            CfNumberConversion.I2L,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("divideUnsigned")),
                false),
             CfStore.LSTORE_5,
            label6,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfConstNumber.constNumber(43, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label7),
            CfLoad.ILOAD_4,
            CfConstNumber.ICONST_1,
            new CfIfCmp(IfType.LE, ValueType.INT, label7),
            CfLoad.ILOAD_1,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.IADD,
            new CfGoto(label8),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.ILOAD_1,
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
             CfStore.ISTORE_7,
            label9,
            CfConstNumber.LCONST_0,
             CfStore.storeLong(8),
            label10,
            CfLoad.ILOAD_7,
             CfStore.storeInt(10),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            CfLoad.loadInt(10),
            CfLoad.ILOAD_2,
            new CfIfCmp(IfType.GE, ValueType.INT, label20),
            label12,
            CfLoad.ALOAD_0,
            CfLoad.loadInt(10),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.charType, factory.intType),
                    factory.createString("charAt")),
                true),
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.charType, factory.intType),
                    factory.createString("digit")),
                false),
             CfStore.storeInt(11),
            label13,
            CfLoad.loadInt(11),
            CfConstNumber.ICONST_M1,
            new CfIfCmp(IfType.NE, ValueType.INT, label15),
            label14,
            new CfNew(factory.createType("Ljava/lang/NumberFormatException;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NumberFormatException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.loadLong(8),
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label17),
            CfLoad.loadLong(8),
            CfLoad.LLOAD_5,
            CfCmp.LCMP,
            new CfIf(IfType.GT, ValueType.INT, label17),
            CfLoad.loadLong(8),
            CfLoad.LLOAD_5,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label18),
            CfLoad.loadInt(11),
            CfConstNumber.constNumber(-1, ValueType.LONG),
            CfLoad.ILOAD_3,
            CfNumberConversion.I2L,
            label16,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("remainderUnsigned")),
                false),
            CfNumberConversion.L2I,
            new CfIfCmp(IfType.LE, ValueType.INT, label18),
            label17,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/NumberFormatException;")),
            CfStackInstruction.DUP,
            new CfConstString(factory.createString("Too large for unsigned long: ")),
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.charSequenceType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                true),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.stringType),
                    factory.createString("concat")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NumberFormatException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label18,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.loadLong(8),
            CfLoad.ILOAD_3,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LMUL,
            CfLoad.loadInt(11),
            CfNumberConversion.I2L,
            CfArithmeticBinop.LADD,
             CfStore.storeLong(8),
            label19,
            new CfIinc(10, 1),
            new CfGoto(label11),
            label20,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.loadLong(8),
            CfReturn.LRETURN,
            label21),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_parseUnsignedLongWithRadix(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_0,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(
                        factory.longType,
                        factory.charSequenceType,
                        factory.intType,
                        factory.intType,
                        factory.intType),
                    factory.createString("parseUnsignedLong")),
                false),
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_remainderUnsigned(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_2,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label6),
            label1,
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.LSTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(6),
            label3,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(6),
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label5),
            label4,
            CfLoad.LLOAD_0,
            CfReturn.LRETURN,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LSUB,
            CfReturn.LRETURN,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label8),
            label7,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LREM,
            CfReturn.LRETURN,
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.LUSHR,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LDIV,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.LSHL,
             CfStore.LSTORE_4,
            label9,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_4,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LMUL,
            CfArithmeticBinop.LSUB,
             CfStore.storeLong(6),
            label10,
            CfLoad.loadLong(6),
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(8),
            label11,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfLogicalBinop.LXOR,
             CfStore.storeLong(10),
            label12,
            CfLoad.loadLong(6),
            CfLoad.loadLong(8),
            CfLoad.loadLong(10),
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label13),
            CfLoad.LLOAD_2,
            new CfGoto(label14),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType()))),
            CfConstNumber.LCONST_0,
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType(), FrameType.longType()))),
            CfArithmeticBinop.LSUB,
            CfReturn.LRETURN,
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_toUnsignedString(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(10, ValueType.INT),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.stringType, factory.longType, factory.intType),
                    factory.createString("toUnsignedString")),
                false),
            CfReturn.ARETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode LongMethods_toUnsignedStringWithRadix(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfConstString(factory.createString("0")),
            CfReturn.ARETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.longType(), FrameType.longHighType(), FrameType.intType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LE, ValueType.INT, label4),
            label3,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.stringType, factory.longType, factory.intType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.longType(), FrameType.longHighType(), FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_2,
            new CfIfCmp(IfType.LT, ValueType.INT, label5),
            CfLoad.ILOAD_2,
            CfConstNumber.constNumber(36, ValueType.INT),
            new CfIfCmp(IfType.LE, ValueType.INT, label6),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.longType(), FrameType.longHighType(), FrameType.intType()
                    })),
            CfConstNumber.constNumber(10, ValueType.INT),
             CfStore.ISTORE_2,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.longType(), FrameType.longHighType(), FrameType.intType()
                    })),
            CfConstNumber.constNumber(64, ValueType.INT),
            new CfNewArray(factory.charArrayType),
             CfStore.ASTORE_3,
            label7,
            CfLoad.ALOAD_3,
            CfArrayLength.INSTANCE,
             CfStore.ISTORE_4,
            label8,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.ISUB,
            CfLogicalBinop.IAND,
            new CfIf(IfType.NE, ValueType.INT, label15),
            label9,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Integer;"),
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("numberOfTrailingZeros")),
                false),
             CfStore.ISTORE_5,
            label10,
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.ISUB,
             CfStore.ISTORE_6,
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_3,
            new CfIinc(4, -1),
            CfLoad.ILOAD_4,
            CfLoad.LLOAD_0,
            CfNumberConversion.L2I,
            CfLoad.ILOAD_6,
            CfLogicalBinop.IAND,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.charType, factory.intType, factory.intType),
                    factory.createString("forDigit")),
                false),
            CfArrayStore.forType(MemberType.CHAR),
            label12,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_5,
            CfLogicalBinop.LUSHR,
             CfStore.LSTORE_0,
            label13,
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label11),
            label14,
            new CfGoto(label25),
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.IAND,
            new CfIf(IfType.NE, ValueType.INT, label18),
            label16,
            CfLoad.LLOAD_0,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.LUSHR,
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_1,
            CfLogicalBinop.IUSHR,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LDIV,
             CfStore.LSTORE_5,
            label17,
            new CfGoto(label19),
            label18,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType()
                    })),
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("divideUnsigned")),
                false),
             CfStore.LSTORE_5,
            label19,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_5,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LMUL,
            CfArithmeticBinop.LSUB,
             CfStore.storeLong(7),
            label20,
            CfLoad.ALOAD_3,
            new CfIinc(4, -1),
            CfLoad.ILOAD_4,
            CfLoad.loadLong(7),
            CfNumberConversion.L2I,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.charType, factory.intType, factory.intType),
                    factory.createString("forDigit")),
                false),
            CfArrayStore.forType(MemberType.CHAR),
            label21,
            CfLoad.LLOAD_5,
             CfStore.LSTORE_0,
            label22,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LE, ValueType.INT, label25),
            label23,
            CfLoad.ALOAD_3,
            new CfIinc(4, -1),
            CfLoad.ILOAD_4,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LREM,
            CfNumberConversion.L2I,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.charType, factory.intType, factory.intType),
                    factory.createString("forDigit")),
                false),
            CfArrayStore.forType(MemberType.CHAR),
            label24,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LDIV,
             CfStore.LSTORE_0,
            new CfGoto(label22),
            label25,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.charArrayType),
                      FrameType.intType()
                    })),
            new CfNew(factory.stringType),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_3,
            CfLoad.ILOAD_4,
            CfLoad.ALOAD_3,
            CfArrayLength.INSTANCE,
            CfLoad.ILOAD_4,
            CfArithmeticBinop.ISUB,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(
                        factory.voidType, factory.charArrayType, factory.intType, factory.intType),
                    factory.createString("<init>")),
                false),
            CfReturn.ARETURN,
            label26),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_absExact(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(-2147483648, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            CfLoad.ILOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("abs")),
                false),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_absExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.longType(), FrameType.longHighType()})),
            CfLoad.LLOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.longType, factory.longType),
                    factory.createString("abs")),
                false),
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_addExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LADD,
             CfStore.LSTORE_2,
            label1,
            CfLoad.LLOAD_2,
            CfNumberConversion.L2I,
             CfStore.ISTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfLoad.ILOAD_4,
            CfNumberConversion.I2L,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            CfLoad.ILOAD_4,
            CfReturn.IRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_addExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LADD,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfLogicalBinop.LXOR,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label2),
            CfConstNumber.ICONST_1,
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfConstNumber.ICONST_0,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_4,
            CfLogicalBinop.LXOR,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label4),
            CfConstNumber.ICONST_1,
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfConstNumber.ICONST_0,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType(), FrameType.intType()))),
            CfLogicalBinop.IOR,
            new CfIf(IfType.EQ, ValueType.INT, label7),
            label6,
            CfLoad.LLOAD_4,
            CfReturn.LRETURN,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_decrementExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(-2147483648, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            CfLoad.ILOAD_0,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_decrementExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.longType(), FrameType.longHighType()})),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_1,
            CfArithmeticBinop.LSUB,
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.IDIV,
             CfStore.ISTORE_2,
            label1,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            CfArithmeticBinop.IMUL,
            CfArithmeticBinop.ISUB,
             CfStore.ISTORE_3,
            label2,
            CfLoad.ILOAD_3,
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            CfLoad.ILOAD_2,
            CfReturn.IRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfConstNumber.ICONST_1,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfLogicalBinop.IXOR,
            CfConstNumber.constNumber(31, ValueType.INT),
            CfLogicalBinop.ISHR,
            CfLogicalBinop.IOR,
             CfStore.ISTORE_4,
            label5,
            CfLoad.ILOAD_4,
            new CfIf(IfType.GE, ValueType.INT, label6),
            CfLoad.ILOAD_2,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.ISUB,
            new CfGoto(label7),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LDIV,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfLoad.LLOAD_4,
            CfArithmeticBinop.LMUL,
            CfArithmeticBinop.LSUB,
             CfStore.storeLong(6),
            label2,
            CfLoad.loadLong(6),
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            CfLoad.LLOAD_4,
            CfReturn.LRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfConstNumber.LCONST_1,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfLogicalBinop.LXOR,
            CfConstNumber.constNumber(63, ValueType.INT),
            CfLogicalBinop.LSHR,
            CfLogicalBinop.LOR,
             CfStore.storeLong(8),
            label5,
            CfLoad.loadLong(8),
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.GE, ValueType.INT, label6),
            CfLoad.LLOAD_4,
            CfConstNumber.LCONST_1,
            CfArithmeticBinop.LSUB,
            new CfGoto(label7),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_4,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType()))),
            CfReturn.LRETURN,
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorDivLongInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("floorDiv")),
                false),
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.IREM,
             CfStore.ISTORE_2,
            label1,
            CfLoad.ILOAD_2,
            new CfIf(IfType.NE, ValueType.INT, label3),
            label2,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(), FrameType.intType(), FrameType.intType()
                    })),
            CfConstNumber.ICONST_1,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfLogicalBinop.IXOR,
            CfConstNumber.constNumber(31, ValueType.INT),
            CfLogicalBinop.ISHR,
            CfLogicalBinop.IOR,
             CfStore.ISTORE_3,
            label4,
            CfLoad.ILOAD_3,
            new CfIf(IfType.LE, ValueType.INT, label5),
            CfLoad.ILOAD_2,
            new CfGoto(label6),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.IADD,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LREM,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_4,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label3),
            label2,
            CfConstNumber.LCONST_0,
            CfReturn.LRETURN,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfConstNumber.LCONST_1,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfLogicalBinop.LXOR,
            CfConstNumber.constNumber(63, ValueType.INT),
            CfLogicalBinop.LSHR,
            CfLogicalBinop.LOR,
             CfStore.storeLong(6),
            label4,
            CfLoad.loadLong(6),
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LE, ValueType.INT, label5),
            CfLoad.LLOAD_4,
            new CfGoto(label6),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_4,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LADD,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.longType()))),
            CfReturn.LRETURN,
            label7),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_floorModLongInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("floorMod")),
                false),
            CfNumberConversion.L2I,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_incrementExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(2147483647, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            CfLoad.ILOAD_0,
            CfConstNumber.ICONST_1,
            CfArithmeticBinop.IADD,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_incrementExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(9223372036854775807L, ValueType.LONG),
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.longType(), FrameType.longHighType()})),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_1,
            CfArithmeticBinop.LADD,
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LMUL,
             CfStore.LSTORE_2,
            label1,
            CfLoad.LLOAD_2,
            CfNumberConversion.L2I,
             CfStore.ISTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfLoad.ILOAD_4,
            CfNumberConversion.I2L,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            CfLoad.ILOAD_4,
            CfReturn.IRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            label1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.intType, factory.longType),
                    factory.createString("numberOfLeadingZeros")),
                false),
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-1, ValueType.LONG),
            CfLogicalBinop.LXOR,
            label2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.intType, factory.longType),
                    factory.createString("numberOfLeadingZeros")),
                false),
            CfArithmeticBinop.IADD,
            CfLoad.LLOAD_2,
            label3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.intType, factory.longType),
                    factory.createString("numberOfLeadingZeros")),
                false),
            CfArithmeticBinop.IADD,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-1, ValueType.LONG),
            CfLogicalBinop.LXOR,
            label4,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Long;"),
                    factory.createProto(factory.intType, factory.longType),
                    factory.createString("numberOfLeadingZeros")),
                false),
            CfArithmeticBinop.IADD,
             CfStore.ISTORE_4,
            label5,
            CfLoad.ILOAD_4,
            CfConstNumber.constNumber(65, ValueType.INT),
            new CfIfCmp(IfType.LE, ValueType.INT, label7),
            label6,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LMUL,
            CfReturn.LRETURN,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfConstNumber.constNumber(64, ValueType.INT),
            new CfIfCmp(IfType.LT, ValueType.INT, label15),
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label8),
            CfConstNumber.ICONST_1,
            new CfGoto(label9),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            CfConstNumber.ICONST_0,
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfCmp.LCMP,
            new CfIf(IfType.EQ, ValueType.INT, label10),
            CfConstNumber.ICONST_1,
            new CfGoto(label11),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfConstNumber.ICONST_0,
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType(), FrameType.intType()))),
            CfLogicalBinop.IOR,
            new CfIf(IfType.EQ, ValueType.INT, label15),
            label12,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LMUL,
             CfStore.LSTORE_5,
            label13,
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.EQ, ValueType.INT, label14),
            CfLoad.LLOAD_5,
            CfLoad.LLOAD_0,
            CfArithmeticBinop.LDIV,
            CfLoad.LLOAD_2,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label15),
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5, 6},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_5,
            CfReturn.LRETURN,
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label16),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyExactLongInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        3,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.longType, factory.longType, factory.longType),
                    factory.createString("multiplyExact")),
                false),
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyFull(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LMUL,
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_multiplyHigh(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LSHR,
             CfStore.storeLong(6),
            label2,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.storeLong(8),
            label3,
            CfLoad.LLOAD_2,
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LSHR,
             CfStore.storeLong(10),
            label4,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(8),
            CfArithmeticBinop.LMUL,
             CfStore.storeLong(12),
            label5,
            CfLoad.loadLong(12),
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LUSHR,
             CfStore.storeLong(14),
            label6,
            CfLoad.loadLong(6),
            CfLoad.loadLong(8),
            CfArithmeticBinop.LMUL,
             CfStore.storeLong(16),
            label7,
            CfLoad.loadLong(16),
            CfLoad.loadLong(14),
            CfArithmeticBinop.LADD,
             CfStore.storeLong(18),
            label8,
            CfLoad.loadLong(18),
            CfConstNumber.constNumber(4294967295L, ValueType.LONG),
            CfLogicalBinop.LAND,
             CfStore.storeLong(20),
            label9,
            CfLoad.loadLong(18),
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LSHR,
             CfStore.storeLong(22),
            label10,
            CfLoad.LLOAD_4,
            CfLoad.loadLong(10),
            CfArithmeticBinop.LMUL,
             CfStore.storeLong(24),
            label11,
            CfLoad.loadLong(24),
            CfLoad.loadLong(20),
            CfArithmeticBinop.LADD,
             CfStore.storeLong(26),
            label12,
            CfLoad.loadLong(26),
            CfConstNumber.constNumber(32, ValueType.INT),
            CfLogicalBinop.LSHR,
             CfStore.storeLong(28),
            label13,
            CfLoad.loadLong(6),
            CfLoad.loadLong(10),
            CfArithmeticBinop.LMUL,
             CfStore.storeLong(30),
            label14,
            CfLoad.loadLong(30),
            CfLoad.loadLong(22),
            CfArithmeticBinop.LADD,
            CfLoad.loadLong(28),
            CfArithmeticBinop.LADD,
            CfReturn.LRETURN,
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_negateExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(-2147483648, ValueType.INT),
            new CfIfCmp(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(new int[] {0}, new FrameType[] {FrameType.intType()})),
            CfLoad.ILOAD_0,
            CfNeg.neg(NumericType.INT),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_negateExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfConstNumber.constNumber(-9223372036854775808L, ValueType.LONG),
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {FrameType.longType(), FrameType.longHighType()})),
            CfLoad.LLOAD_0,
            CfNeg.neg(NumericType.LONG),
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_nextDownDouble(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            CfLoad.DLOAD_0,
            CfNeg.neg(NumericType.DOUBLE),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.doubleType, factory.doubleType),
                    factory.createString("nextUp")),
                false),
            CfNeg.neg(NumericType.DOUBLE),
            CfReturn.DRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_nextDownFloat(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            CfLoad.FLOAD_0,
            CfNeg.neg(NumericType.FLOAT),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/Math;"),
                    factory.createProto(factory.floatType, factory.floatType),
                    factory.createString("nextUp")),
                false),
            CfNeg.neg(NumericType.FLOAT),
            CfReturn.FRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_subtractExactInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfLoad.ILOAD_1,
            CfNumberConversion.I2L,
            CfArithmeticBinop.LSUB,
             CfStore.LSTORE_2,
            label1,
            CfLoad.LLOAD_2,
            CfNumberConversion.L2I,
             CfStore.ISTORE_4,
            label2,
            CfLoad.LLOAD_2,
            CfLoad.ILOAD_4,
            CfNumberConversion.I2L,
            CfCmp.LCMP,
            new CfIf(IfType.NE, ValueType.INT, label4),
            label3,
            CfLoad.ILOAD_4,
            CfReturn.IRETURN,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_subtractExactLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LSUB,
             CfStore.LSTORE_4,
            label1,
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfLogicalBinop.LXOR,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label2),
            CfConstNumber.ICONST_1,
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfConstNumber.ICONST_0,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_4,
            CfLogicalBinop.LXOR,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label4),
            CfConstNumber.ICONST_1,
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfConstNumber.ICONST_0,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType(), FrameType.intType()))),
            CfLogicalBinop.IOR,
            new CfIf(IfType.EQ, ValueType.INT, label7),
            label6,
            CfLoad.LLOAD_4,
            CfReturn.LRETURN,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MathMethods_toIntExact(DexItemFactory factory, DexMethod method) {
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
            CfLoad.LLOAD_0,
            CfNumberConversion.L2I,
             CfStore.ISTORE_2,
            label1,
            CfLoad.LLOAD_0,
            CfLoad.ILOAD_2,
            CfNumberConversion.I2L,
            CfCmp.LCMP,
            new CfIf(IfType.EQ, ValueType.INT, label3),
            label2,
            new CfNew(factory.createType("Ljava/lang/ArithmeticException;")),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/ArithmeticException;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.longType(), FrameType.longHighType(), FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode MethodMethods_getParameterCount(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getParameterTypes")),
                false),
            CfArrayLength.INSTANCE,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromIndexSize(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.ILOAD_1,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.ILOAD_2,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            new CfIfCmp(IfType.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(), FrameType.intType(), FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Range [")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" + ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(), FrameType.intType(), FrameType.intType()
                    })),
            CfLoad.ILOAD_0,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromIndexSizeLong(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        6,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.LLOAD_2,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.LLOAD_4,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_4,
            CfLoad.LLOAD_2,
            CfArithmeticBinop.LSUB,
            CfCmp.LCMP,
            new CfIf(IfType.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Range [")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" + ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_4,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromToIndex(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.GT, ValueType.INT, label1),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfIfCmp(IfType.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(), FrameType.intType(), FrameType.intType()
                    })),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Range [")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.intType(), FrameType.intType(), FrameType.intType()
                    })),
            CfLoad.ILOAD_0,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkFromToIndexLong(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        6,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfCmp.LCMP,
            new CfIf(IfType.GT, ValueType.INT, label1),
            CfLoad.LLOAD_2,
            CfLoad.LLOAD_4,
            CfCmp.LCMP,
            new CfIf(IfType.LE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Range [")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(", ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(") out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_4,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkIndex(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ILOAD_0,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.LT, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Index ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1}, new FrameType[] {FrameType.intType(), FrameType.intType()})),
            CfLoad.ILOAD_0,
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_checkIndexLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        5,
        4,
        ImmutableList.of(
            label0,
            CfLoad.LLOAD_0,
            CfConstNumber.LCONST_0,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label1),
            CfLoad.LLOAD_0,
            CfLoad.LLOAD_2,
            CfCmp.LCMP,
            new CfIf(IfType.LT, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            new CfNew(factory.createType("Ljava/lang/IndexOutOfBoundsException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Index ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" out of bounds for length ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.LLOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.longType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IndexOutOfBoundsException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.longType(),
                      FrameType.longHighType()
                    })),
            CfLoad.LLOAD_0,
            CfReturn.LRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_compare(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfIfCmp(IfType.NE, ValueType.OBJECT, label1),
            CfConstNumber.ICONST_0,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Comparator;"))
                    })),
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Comparator;"),
                    factory.createProto(factory.intType, factory.objectType, factory.objectType),
                    factory.createString("compare")),
                true),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Comparator;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_deepEquals(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfIfCmp(IfType.NE, ValueType.OBJECT, label1),
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label2),
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.booleanArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label6),
            label3,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.booleanArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label4),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.booleanArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.booleanArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.booleanArrayType, factory.booleanArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label4),
            CfConstNumber.ICONST_1,
            new CfGoto(label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.byteArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label7,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.byteArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.byteArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.byteArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.byteArrayType, factory.byteArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            CfConstNumber.ICONST_1,
            new CfGoto(label9),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.charArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label14),
            label11,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.charArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label12),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.charArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.charArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.charArrayType, factory.charArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label12),
            CfConstNumber.ICONST_1,
            new CfGoto(label13),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.doubleArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label18),
            label15,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.doubleArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label16),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.doubleArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.doubleArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.doubleArrayType, factory.doubleArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label16),
            CfConstNumber.ICONST_1,
            new CfGoto(label17),
            label16,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label17,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label18,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.floatArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label22),
            label19,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.floatArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label20),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.floatArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.floatArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.floatArrayType, factory.floatArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label20),
            CfConstNumber.ICONST_1,
            new CfGoto(label21),
            label20,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label21,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label22,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.intArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label26),
            label23,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.intArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label24),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.intArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.intArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.intArrayType, factory.intArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label24),
            CfConstNumber.ICONST_1,
            new CfGoto(label25),
            label24,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label25,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label26,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.longArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label30),
            label27,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.longArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label28),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.longArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.longArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.longArrayType, factory.longArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label28),
            CfConstNumber.ICONST_1,
            new CfGoto(label29),
            label28,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label29,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label30,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.shortArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label34),
            label31,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.shortArrayType),
            new CfIf(IfType.EQ, ValueType.INT, label32),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.shortArrayType),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.shortArrayType),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType, factory.shortArrayType, factory.shortArrayType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label32),
            CfConstNumber.ICONST_1,
            new CfGoto(label33),
            label32,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label33,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label34,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            new CfInstanceOf(factory.createType("[Ljava/lang/Object;")),
            new CfIf(IfType.EQ, ValueType.INT, label38),
            label35,
            CfLoad.ALOAD_1,
            new CfInstanceOf(factory.createType("[Ljava/lang/Object;")),
            new CfIf(IfType.EQ, ValueType.INT, label36),
            CfLoad.ALOAD_0,
            new CfCheckCast(factory.createType("[Ljava/lang/Object;")),
            CfLoad.ALOAD_1,
            new CfCheckCast(factory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Arrays;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.createType("[Ljava/lang/Object;"),
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("deepEquals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label36),
            CfConstNumber.ICONST_1,
            new CfGoto(label37),
            label36,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label37,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label38,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("equals")),
                false),
            CfReturn.IRETURN,
            label39),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_equals(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfIfCmp(IfType.EQ, ValueType.OBJECT, label1),
            CfLoad.ALOAD_0,
            new CfIf(IfType.EQ, ValueType.OBJECT, label2),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("equals")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_1,
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfConstNumber.ICONST_0,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_hashCode(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            CfConstNumber.ICONST_0,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.intType),
                    factory.createString("hashCode")),
                false),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_isNull(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_nonNull(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.EQ, ValueType.OBJECT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)}),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullElse(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            new CfIf(IfType.EQ, ValueType.OBJECT, label1),
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_1,
            new CfConstString(factory.createString("defaultObj")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.stringType),
                    factory.createString("requireNonNull")),
                false),
            CfReturn.ARETURN,
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullElseGet(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.EQ, ValueType.OBJECT, label1),
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Supplier;"))
                    })),
            CfLoad.ALOAD_1,
            new CfConstString(factory.createString("supplier")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.stringType),
                    factory.createString("requireNonNull")),
                false),
            new CfCheckCast(factory.createType("Ljava/util/function/Supplier;")),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/Supplier;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                true),
            CfStore.ASTORE_2,
            label2,
            CfLoad.ALOAD_2,
            new CfConstString(factory.createString("supplier.get()")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.stringType),
                    factory.createString("requireNonNull")),
                false),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullMessage(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_1,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_requireNonNullSupplier(
      DexItemFactory factory, DexMethod method) {
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
        3,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label5),
            label1,
            CfLoad.ALOAD_1,
            new CfIf(IfType.EQ, ValueType.OBJECT, label2),
            CfLoad.ALOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/Supplier;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                true),
            new CfCheckCast(factory.stringType),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Supplier;"))
                    })),
            CfConstNull.INSTANCE,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Supplier;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.stringType)))),
            CfStore.ASTORE_2,
            label4,
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            CfStackInstruction.DUP,
            CfLoad.ALOAD_2,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Supplier;"))
                    })),
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label6),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toString(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            new CfConstString(factory.createString("null")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.stringType, factory.objectType, factory.stringType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ObjectsMethods_toStringDefault(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            CfLoad.ALOAD_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.objectType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.stringType)))),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElse(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/Consumer;"),
                    factory.createProto(factory.voidType, factory.objectType),
                    factory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Consumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/lang/Runnable;"),
                    factory.createProto(factory.voidType),
                    factory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Consumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfReturnVoid.INSTANCE,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseDouble(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalDouble;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalDouble;"),
                    factory.createProto(factory.doubleType),
                    factory.createString("getAsDouble")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/DoubleConsumer;"),
                    factory.createProto(factory.voidType, factory.doubleType),
                    factory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalDouble;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/DoubleConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/lang/Runnable;"),
                    factory.createProto(factory.voidType),
                    factory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalDouble;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/DoubleConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfReturnVoid.INSTANCE,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseInt(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalInt;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalInt;"),
                    factory.createProto(factory.intType),
                    factory.createString("getAsInt")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/IntConsumer;"),
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalInt;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/IntConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/lang/Runnable;"),
                    factory.createProto(factory.voidType),
                    factory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalInt;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/IntConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfReturnVoid.INSTANCE,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_ifPresentOrElseLong(
      DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalLong;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalLong;"),
                    factory.createProto(factory.longType),
                    factory.createString("getAsLong")),
                false),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/LongConsumer;"),
                    factory.createProto(factory.voidType, factory.longType),
                    factory.createString("accept")),
                true),
            new CfGoto(label3),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalLong;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/LongConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/lang/Runnable;"),
                    factory.createProto(factory.voidType),
                    factory.createString("run")),
                true),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalLong;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/LongConsumer;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Runnable;"))
                    })),
            CfReturnVoid.INSTANCE,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmpty(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;"))
                    })),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyDouble(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalDouble;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalDouble;"))
                    })),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalDouble;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalInt;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalInt;"))
                    })),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalInt;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_isEmptyLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalLong;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label1),
            CfConstNumber.ICONST_1,
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalLong;"))
                    })),
            CfConstNumber.ICONST_0,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalLong;"))
                    }),
                new ArrayDeque<>(Arrays.asList(FrameType.intType()))),
            CfReturn.IRETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_or(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            CfStackInstruction.POP,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label3),
            label2,
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/function/Supplier;"))
                    })),
            CfLoad.ALOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/Supplier;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                true),
            new CfCheckCast(factory.createType("Ljava/util/Optional;")),
            CfStore.ASTORE_2,
            label4,
            CfLoad.ALOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/Objects;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("requireNonNull")),
                false),
            new CfCheckCast(factory.createType("Ljava/util/Optional;")),
            CfReturn.ARETURN,
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_stream(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/Optional;"),
                    factory.createProto(factory.objectType),
                    factory.createString("get")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/Stream;"),
                    factory.createProto(
                        factory.createType("Ljava/util/stream/Stream;"), factory.objectType),
                    factory.createString("of")),
                true),
            CfReturn.ARETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Optional;"))
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/Stream;"),
                    factory.createProto(factory.createType("Ljava/util/stream/Stream;")),
                    factory.createString("empty")),
                true),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamDouble(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalDouble;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalDouble;"),
                    factory.createProto(factory.doubleType),
                    factory.createString("getAsDouble")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/DoubleStream;"),
                    factory.createProto(
                        factory.createType("Ljava/util/stream/DoubleStream;"), factory.doubleType),
                    factory.createString("of")),
                true),
            CfReturn.ARETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalDouble;"))
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/DoubleStream;"),
                    factory.createProto(factory.createType("Ljava/util/stream/DoubleStream;")),
                    factory.createString("empty")),
                true),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamInt(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalInt;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalInt;"),
                    factory.createProto(factory.intType),
                    factory.createString("getAsInt")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/IntStream;"),
                    factory.createProto(
                        factory.createType("Ljava/util/stream/IntStream;"), factory.intType),
                    factory.createString("of")),
                true),
            CfReturn.ARETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalInt;"))
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/IntStream;"),
                    factory.createProto(factory.createType("Ljava/util/stream/IntStream;")),
                    factory.createString("empty")),
                true),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode OptionalMethods_streamLong(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalLong;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("isPresent")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/OptionalLong;"),
                    factory.createProto(factory.longType),
                    factory.createString("getAsLong")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/LongStream;"),
                    factory.createProto(
                        factory.createType("Ljava/util/stream/LongStream;"), factory.longType),
                    factory.createString("of")),
                true),
            CfReturn.ARETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/OptionalLong;"))
                    })),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/LongStream;"),
                    factory.createProto(factory.createType("Ljava/util/stream/LongStream;")),
                    factory.createString("empty")),
                true),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode PredicateMethods_not(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_0,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/function/Predicate;"),
                    factory.createProto(factory.createType("Ljava/util/function/Predicate;")),
                    factory.createString("negate")),
                true),
            CfReturn.ARETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_compare(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_compareUnsigned(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(65535, ValueType.INT),
            CfLogicalBinop.IAND,
            CfLoad.ILOAD_1,
            CfConstNumber.constNumber(65535, ValueType.INT),
            CfLogicalBinop.IAND,
            CfArithmeticBinop.ISUB,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_toUnsignedInt(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfConstNumber.constNumber(65535, ValueType.INT),
            CfLogicalBinop.IAND,
            CfReturn.IRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ShortMethods_toUnsignedLong(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        1,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_0,
            CfNumberConversion.I2L,
            CfConstNumber.constNumber(65535, ValueType.LONG),
            CfLogicalBinop.LAND,
            CfReturn.LRETURN,
            label1),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StreamMethods_ofNullable(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/Stream;"),
                    factory.createProto(factory.createType("Ljava/util/stream/Stream;")),
                    factory.createString("empty")),
                true),
            new CfGoto(label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)})),
            CfLoad.ALOAD_0,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/util/stream/Stream;"),
                    factory.createProto(
                        factory.createType("Ljava/util/stream/Stream;"), factory.objectType),
                    factory.createString("of")),
                true),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.objectType)}),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/util/stream/Stream;"))))),
            CfReturn.ARETURN,
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_isBlank(DexItemFactory factory, DexMethod method) {
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
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
             CfStore.ISTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfIfCmp(IfType.GE, ValueType.INT, label8),
            label3,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("codePointAt")),
                false),
             CfStore.ISTORE_3,
            label4,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isWhitespace")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label6),
            label5,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("charCount")),
                false),
            CfArithmeticBinop.IADD,
             CfStore.ISTORE_1,
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.stringType)})),
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinArray(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            CfStackInstruction.DUP,
            new CfConstString(factory.createString("delimiter")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/CharSequence;"))
                    })),
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_2,
            label2,
            CfLoad.ALOAD_1,
            CfArrayLength.INSTANCE,
            new CfIf(IfType.LE, ValueType.INT, label9),
            label3,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_1,
            CfConstNumber.ICONST_0,
            CfArrayLoad.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label4,
            CfConstNumber.ICONST_1,
             CfStore.ISTORE_3,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/CharSequence;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_3,
            CfLoad.ALOAD_1,
            CfArrayLength.INSTANCE,
            new CfIfCmp(IfType.GE, ValueType.INT, label9),
            label6,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label7,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_1,
            CfLoad.ILOAD_3,
            CfArrayLoad.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label8,
            new CfIinc(3, 1),
            new CfGoto(label5),
            label9,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/CharSequence;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType)
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label10),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_joinIterable(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfIf(IfType.NE, ValueType.OBJECT, label1),
            new CfNew(factory.createType("Ljava/lang/NullPointerException;")),
            CfStackInstruction.DUP,
            new CfConstString(factory.createString("delimiter")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/NullPointerException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Iterable;"))
                    })),
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            CfStore.ASTORE_2,
            label2,
            CfLoad.ALOAD_1,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/lang/Iterable;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                true),
             CfStore.ASTORE_3,
            label3,
            CfLoad.ALOAD_3,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            label4,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_3,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
            new CfCheckCast(factory.charSequenceType),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Iterable;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            CfLoad.ALOAD_3,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label8),
            label6,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label7,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_3,
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
            new CfCheckCast(factory.charSequenceType),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charSequenceType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            new CfGoto(label5),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.charSequenceType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/Iterable;")),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_repeat(DexItemFactory factory, DexMethod method) {
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
        4,
        5,
        ImmutableList.of(
            label0,
            CfLoad.ILOAD_1,
            new CfIf(IfType.GE, ValueType.INT, label2),
            label1,
            new CfNew(factory.createType("Ljava/lang/IllegalArgumentException;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("count is negative: ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/IllegalArgumentException;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType), FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
             CfStore.ISTORE_2,
            label3,
            CfLoad.ILOAD_1,
            new CfIf(IfType.EQ, ValueType.INT, label4),
            CfLoad.ILOAD_2,
            new CfIf(IfType.NE, ValueType.INT, label5),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfConstString(factory.createString("")),
            CfReturn.ARETURN,
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfConstNumber.ICONST_1,
            new CfIfCmp(IfType.NE, ValueType.INT, label7),
            label6,
            CfLoad.ALOAD_0,
            CfReturn.ARETURN,
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
            CfConstNumber.constNumber(2147483647, ValueType.INT),
            CfLoad.ILOAD_1,
            CfArithmeticBinop.IDIV,
            new CfIfCmp(IfType.LE, ValueType.INT, label10),
            label8,
            new CfNew(factory.createType("Ljava/lang/OutOfMemoryError;")),
            CfStackInstruction.DUP,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("Repeating ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_0,
            label9,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(factory.createString(" bytes String ")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.intType),
                    factory.createString("append")),
                false),
            new CfConstString(
                factory.createString(" times will produce a String exceeding maximum size.")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/OutOfMemoryError;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("<init>")),
                false),
            CfThrow.INSTANCE,
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            CfArithmeticBinop.IMUL,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType, factory.intType),
                    factory.createString("<init>")),
                false),
             CfStore.ASTORE_3,
            label11,
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_4,
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringBuilderType),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_4,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.GE, ValueType.INT, label15),
            label13,
            CfLoad.ALOAD_3,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfStackInstruction.POP,
            label14,
            new CfIinc(4, 1),
            new CfGoto(label12),
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.initializedNonNullReference(factory.stringBuilderType)
                    })),
            CfLoad.ALOAD_3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            CfReturn.ARETURN,
            label16),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_strip(DexItemFactory factory, DexMethod method) {
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
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
             CfStore.ISTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfIfCmp(IfType.GE, ValueType.INT, label8),
            label3,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("codePointAt")),
                false),
             CfStore.ISTORE_3,
            label4,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isWhitespace")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label6),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("charCount")),
                false),
            CfArithmeticBinop.IADD,
             CfStore.ISTORE_1,
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_1,
            new CfIfCmp(IfType.LE, ValueType.INT, label14),
            label9,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.charSequenceType, factory.intType),
                    factory.createString("codePointBefore")),
                false),
             CfStore.ISTORE_3,
            label10,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isWhitespace")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label12),
            label11,
            new CfGoto(label14),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_2,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("charCount")),
                false),
            CfArithmeticBinop.ISUB,
             CfStore.ISTORE_2,
            label13,
            new CfGoto(label8),
            label14,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType, factory.intType),
                    factory.createString("substring")),
                false),
            CfReturn.ARETURN,
            label15),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_stripLeading(DexItemFactory factory, DexMethod method) {
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
            CfConstNumber.ICONST_0,
             CfStore.ISTORE_1,
            label1,
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
             CfStore.ISTORE_2,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfIfCmp(IfType.GE, ValueType.INT, label8),
            label3,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("codePointAt")),
                false),
             CfStore.ISTORE_3,
            label4,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isWhitespace")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label6),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_3,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("charCount")),
                false),
            CfArithmeticBinop.IADD,
             CfStore.ISTORE_1,
            label7,
            new CfGoto(label2),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType, factory.intType),
                    factory.createString("substring")),
                false),
            CfReturn.ARETURN,
            label9),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode StringMethods_stripTrailing(DexItemFactory factory, DexMethod method) {
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
            CfLoad.ALOAD_0,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.intType),
                    factory.createString("length")),
                false),
             CfStore.ISTORE_1,
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType), FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            new CfIf(IfType.LE, ValueType.INT, label7),
            label2,
            CfLoad.ALOAD_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.charSequenceType, factory.intType),
                    factory.createString("codePointBefore")),
                false),
             CfStore.ISTORE_2,
            label3,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isWhitespace")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label5),
            label4,
            new CfGoto(label7),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            CfLoad.ILOAD_1,
            CfLoad.ILOAD_2,
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.boxedCharType,
                    factory.createProto(factory.intType, factory.intType),
                    factory.createString("charCount")),
                false),
            CfArithmeticBinop.ISUB,
             CfStore.ISTORE_1,
            label6,
            new CfGoto(label1),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.stringType), FrameType.intType()
                    })),
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_0,
            CfLoad.ILOAD_1,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType, factory.intType),
                    factory.createString("substring")),
                false),
            CfReturn.ARETURN,
            label8),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode ThrowableMethods_addSuppressed(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        3,
        ImmutableList.of(
            label0,
            new CfConstClass(factory.throwableType),
            new CfConstString(factory.createString("addSuppressed")),
            CfConstNumber.ICONST_1,
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            new CfConstClass(factory.throwableType),
            CfArrayStore.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredMethod")),
                false),
            CfStore.ASTORE_2,
            label1,
            CfLoad.ALOAD_2,
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_1,
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            CfStackInstruction.DUP,
            CfConstNumber.ICONST_0,
            CfLoad.ALOAD_1,
            CfArrayStore.forType(MemberType.OBJECT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            CfStackInstruction.POP,
            label2,
            new CfGoto(label4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            CfStore.ASTORE_2,
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(factory.throwableType),
                      FrameType.initializedNonNullReference(factory.throwableType)
                    })),
            CfReturnVoid.INSTANCE,
            label5),
        ImmutableList.of(
            new CfTryCatch(
                label0,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label3))),
        ImmutableList.of());
  }

  public static CfCode ThrowableMethods_getSuppressed(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        2,
        ImmutableList.of(
            label0,
            new CfConstClass(factory.throwableType),
            new CfConstString(factory.createString("getSuppressed")),
            CfConstNumber.ICONST_0,
            new CfNewArray(factory.createType("[Ljava/lang/Class;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Method;"),
                        factory.stringType,
                        factory.createType("[Ljava/lang/Class;")),
                    factory.createString("getDeclaredMethod")),
                false),
            CfStore.ASTORE_1,
            label1,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_0,
            CfConstNumber.ICONST_0,
            new CfNewArray(factory.createType("[Ljava/lang/Object;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Method;"),
                    factory.createProto(
                        factory.objectType,
                        factory.objectType,
                        factory.createType("[Ljava/lang/Object;")),
                    factory.createString("invoke")),
                false),
            new CfCheckCast(factory.createType("[Ljava/lang/Throwable;")),
            label2,
            CfReturn.ARETURN,
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {FrameType.initializedNonNullReference(factory.throwableType)}),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            CfStore.ASTORE_1,
            label4,
            CfConstNumber.ICONST_0,
            new CfNewArray(factory.createType("[Ljava/lang/Throwable;")),
            CfReturn.ARETURN,
            label5),
        ImmutableList.of(
            new CfTryCatch(
                label0,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label3))),
        ImmutableList.of());
  }

  public static CfCode UnsafeMethods_compareAndSwapObject(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        6,
        ImmutableList.of(
            label0,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Lsun/misc/Unsafe;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.LLOAD_2,
            CfLoad.ALOAD_4,
            CfLoad.ALOAD_5,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.objectType,
                        factory.longType,
                        factory.objectType,
                        factory.objectType),
                    factory.createString("compareAndSwapObject")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label2),
            label1,
            CfConstNumber.ICONST_1,
            CfReturn.IRETURN,
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4, 5},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Lsun/misc/Unsafe;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.LLOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.longType),
                    factory.createString("getObject")),
                false),
            CfLoad.ALOAD_4,
            new CfIfCmp(IfType.EQ, ValueType.OBJECT, label0),
            label3,
            CfConstNumber.ICONST_0,
            CfReturn.IRETURN,
            label4),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
