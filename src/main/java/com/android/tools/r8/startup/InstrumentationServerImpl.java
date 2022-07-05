// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See InstrumentationServerClassGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.startup;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

public final class InstrumentationServerImpl {
  public static DexProgramClass createClass(DexItemFactory dexItemFactory) {
    return new DexProgramClass(
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
        Kind.CF,
        Origin.unknown(),
        ClassAccessFlags.fromCfAccessFlags(1),
        null,
        DexTypeList.empty(),
        dexItemFactory.createString("InstrumentationServerImpl"),
        NestHostClassAttribute.none(),
        Collections.emptyList(),
        Collections.emptyList(),
        EnclosingMethodAttribute.none(),
        Collections.emptyList(),
        ClassSignature.noSignature(),
        DexAnnotationSet.empty(),
        createStaticFields(dexItemFactory),
        createInstanceFields(dexItemFactory),
        MethodCollectionFactory.fromMethods(
            createDirectMethods(dexItemFactory), createVirtualMethods(dexItemFactory)),
        dexItemFactory.getSkipNameValidationForTesting(),
        DexProgramClass::invalidChecksumRequest);
  }

  private static DexEncodedField[] createInstanceFields(DexItemFactory dexItemFactory) {
    return new DexEncodedField[] {
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType("Ljava/lang/StringBuilder;"),
                  dexItemFactory.createString("builder")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(18))
          .setApiLevel(ComputedApiLevel.unknown())
          .build(),
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType("Z"),
                  dexItemFactory.createString("writeToLogcat")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(18))
          .setApiLevel(ComputedApiLevel.unknown())
          .build(),
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType("Ljava/lang/String;"),
                  dexItemFactory.createString("logcatTag")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(18))
          .setApiLevel(ComputedApiLevel.unknown())
          .build()
    };
  }

  private static DexEncodedField[] createStaticFields(DexItemFactory dexItemFactory) {
    return new DexEncodedField[] {
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createString("INSTANCE")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(26))
          .setApiLevel(ComputedApiLevel.unknown())
          .build()
    };
  }

  private static DexEncodedMethod[] createDirectMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[0];
  }

  private static DexEncodedMethod[] createVirtualMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[0];
  }

  public static CfCode createClassInitializerCfCode(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                options.itemFactory.createType(
                    "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createString("INSTANCE"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createInstanceInitializerCfCode1(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServer;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfInstanceFieldWrite(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createString("builder"))),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(0, ValueType.INT),
            new CfInstanceFieldWrite(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.booleanType,
                    options.itemFactory.createString("writeToLogcat"))),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstString(options.itemFactory.createString("r8")),
            new CfInstanceFieldWrite(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.stringType,
                    options.itemFactory.createString("logcatTag"))),
            label4,
            new CfReturnVoid(),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode2_addLine(InternalOptions options, DexMethod method) {
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
            new CfInstanceFieldRead(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createString("builder"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.stringType),
                    options.itemFactory.createString("append")),
                false),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.charType),
                    options.itemFactory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode3_addNonSyntheticMethod(
      InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType(
                            "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                    options.itemFactory.createString("getInstance")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("addLine")),
                false),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode4_addSyntheticMethod(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        3,
        1,
        ImmutableList.of(
            label0,
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType(
                            "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                    options.itemFactory.createString("getInstance")),
                false),
            new CfNew(options.itemFactory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("<init>")),
                false),
            new CfConstNumber(83, ValueType.INT),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(
                        options.itemFactory.stringBuilderType, options.itemFactory.charType),
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
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.stringType),
                    options.itemFactory.createString("addLine")),
                false),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode5_getInstance(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.createString("INSTANCE"))),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode6_writeToFile(InternalOptions options, DexMethod method) {
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
            new CfNew(options.itemFactory.createType("Ljava/io/FileOutputStream;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/io/FileOutputStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType,
                        options.itemFactory.createType("Ljava/io/File;")),
                    options.itemFactory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                options.itemFactory.createField(
                    options.itemFactory.createType(
                        "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createString("builder"))),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringBuilderType,
                    options.itemFactory.createProto(options.itemFactory.stringType),
                    options.itemFactory.createString("toString")),
                false),
            new CfConstString(options.itemFactory.createString("UTF-8")),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/nio/charset/Charset;"),
                    options.itemFactory.createProto(
                        options.itemFactory.createType("Ljava/nio/charset/Charset;"),
                        options.itemFactory.stringType),
                    options.itemFactory.createString("forName")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.stringType,
                    options.itemFactory.createProto(
                        options.itemFactory.byteArrayType,
                        options.itemFactory.createType("Ljava/nio/charset/Charset;")),
                    options.itemFactory.createString("getBytes")),
                false),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/io/FileOutputStream;"),
                    options.itemFactory.createProto(
                        options.itemFactory.voidType, options.itemFactory.byteArrayType),
                    options.itemFactory.createString("write")),
                false),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/io/FileOutputStream;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("close")),
                false),
            label3,
            new CfGoto(label6),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType("Ljava/io/FileOutputStream;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(options.itemFactory.throwableType)))),
            new CfStore(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Ljava/io/FileOutputStream;"),
                    options.itemFactory.createProto(options.itemFactory.voidType),
                    options.itemFactory.createString("close")),
                false),
            label5,
            new CfLoad(ValueType.OBJECT, 3),
            new CfThrow(),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          options.itemFactory.createType("Ljava/io/FileOutputStream;"))
                    })),
            new CfReturnVoid(),
            label7),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label2,
                ImmutableList.of(options.itemFactory.throwableType),
                ImmutableList.of(label4))),
        ImmutableList.of());
  }

  public static CfCode createCfCode7_writeToLogcat(InternalOptions options, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfConstString(options.itemFactory.createString("r8")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                options.itemFactory.createMethod(
                    options.itemFactory.createType("Landroid/util/Log;"),
                    options.itemFactory.createProto(
                        options.itemFactory.intType,
                        options.itemFactory.stringType,
                        options.itemFactory.stringType),
                    options.itemFactory.createString("v")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
