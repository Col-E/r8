// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateDesugaredLibraryBridge.java.
// ***********************************************************************************

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class DesugaredLibraryCfMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Landroidx/navigation/NavType$Companion;");
    factory.createSynthesizedType("Landroidx/navigation/NavType;");
    factory.createSynthesizedType("Ljava/lang/ClassNotFoundException;");
    factory.createSynthesizedType("Ljava/lang/RuntimeException;");
  }

  public static CfCode DesugaredLibraryBridge_fromArgType(
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
    return new CfCode(
        method.holder,
        4,
        4,
        ImmutableList.of(
            label0,
            CfLoad.ALOAD_1,
            new CfIf(IfType.EQ, ValueType.OBJECT, label1),
            CfLoad.ALOAD_1,
            new CfConstString(factory.createString("java")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(factory.booleanType, factory.stringType),
                    factory.createString("startsWith")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label2),
            label1,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Landroidx/navigation/NavType$Companion;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Landroidx/navigation/NavType$Companion;"),
                    factory.createProto(
                        factory.createType("Landroidx/navigation/NavType;"),
                        factory.stringType,
                        factory.stringType),
                    factory.createString("fromArgType")),
                false),
            new CfReturn(ValueType.OBJECT),
            label2,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Landroidx/navigation/NavType$Companion;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            CfLoad.ALOAD_0,
            new CfNew(factory.stringBuilderType),
            CfStackInstruction.DUP,
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstString(factory.createString("j$")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            CfLoad.ALOAD_1,
            new CfConstString(factory.createString("java")),
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
                    factory.stringType,
                    factory.createProto(factory.stringType, factory.intType),
                    factory.createString("substring")),
                false),
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
                182,
                factory.createMethod(
                    factory.createType("Landroidx/navigation/NavType$Companion;"),
                    factory.createProto(
                        factory.createType("Landroidx/navigation/NavType;"),
                        factory.stringType,
                        factory.stringType),
                    factory.createString("fromArgType")),
                false),
            label3,
            new CfReturn(ValueType.OBJECT),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Landroidx/navigation/NavType$Companion;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/RuntimeException;"))))),
            CfStore.ASTORE_2,
            label5,
            CfLoad.ALOAD_3,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.throwableType),
                    factory.createString("getCause")),
                false),
            new CfInstanceOf(factory.createType("Ljava/lang/ClassNotFoundException;")),
            new CfIf(IfType.EQ, ValueType.INT, label7),
            label6,
            CfLoad.ALOAD_0,
            CfLoad.ALOAD_1,
            CfLoad.ALOAD_2,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Landroidx/navigation/NavType$Companion;"),
                    factory.createProto(
                        factory.createType("Landroidx/navigation/NavType;"),
                        factory.stringType,
                        factory.stringType),
                    factory.createString("fromArgType")),
                false),
            new CfReturn(ValueType.OBJECT),
            label7,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Landroidx/navigation/NavType$Companion;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/RuntimeException;"))
                    })),
            CfLoad.ALOAD_3,
            CfThrow.INSTANCE,
            label8),
        ImmutableList.of(
            new CfTryCatch(
                label2,
                label3,
                ImmutableList.of(factory.createType("Ljava/lang/RuntimeException;")),
                ImmutableList.of(label4))),
        ImmutableList.of());
  }
}
