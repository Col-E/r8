// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See InstrumentationServerClassGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.startup.generated;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
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
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

public final class InstrumentationServerImplFactory {
  public static DexProgramClass createClass(DexItemFactory dexItemFactory) {
    return new DexProgramClass(
        dexItemFactory.createType(
            "Lcom/android/tools/r8/startup/generated/InstrumentationServerImplFactory;"),
        Kind.CF,
        Origin.unknown(),
        ClassAccessFlags.fromCfAccessFlags(1),
        null,
        DexTypeList.empty(),
        dexItemFactory.createString("InstrumentationServerImplFactory"),
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
                  dexItemFactory.createType("Ljava/lang/String;"),
                  dexItemFactory.createString("logcatTag")))
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
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(2, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createInstanceInitializer(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")))
          .setCode(method -> createInstanceInitializerCfCode1(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(34, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"),
                      dexItemFactory.createType("Ljava/lang/String;")),
                  dexItemFactory.createString("addLine")))
          .setCode(method -> createCfCode2_addLine(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(9, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"),
                      dexItemFactory.createType("Ljava/lang/String;")),
                  dexItemFactory.createString("addNonSyntheticMethod")))
          .setCode(method -> createCfCode3_addNonSyntheticMethod(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(9, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"),
                      dexItemFactory.createType("Ljava/lang/String;")),
                  dexItemFactory.createString("addSyntheticMethod")))
          .setCode(method -> createCfCode4_addSyntheticMethod(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(9, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType(
                          "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                  dexItemFactory.createString("getInstance")))
          .setCode(method -> createCfCode5_getInstance(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(2, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"),
                      dexItemFactory.createType("Ljava/lang/String;")),
                  dexItemFactory.createString("writeToLogcat")))
          .setCode(method -> createCfCode7_writeToLogcat(dexItemFactory, method))
          .build()
    };
  }

  private static DexEncodedMethod[] createVirtualMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(33, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"), dexItemFactory.createType("Ljava/io/File;")),
                  dexItemFactory.createString("writeToFile")))
          .setCode(method -> createCfCode6_writeToFile(dexItemFactory, method))
          .build()
    };
  }

  public static CfCode createClassInitializerCfCode(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        0,
        ImmutableList.of(
            label0,
            new CfNew(
                factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfStaticFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createString("INSTANCE"))),
            new CfReturnVoid()),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createInstanceInitializerCfCode1(DexItemFactory factory, DexMethod method) {
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
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfNew(factory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.stringBuilderType,
                    factory.createString("builder"))),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(0, ValueType.INT),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.booleanType,
                    factory.createString("writeToLogcat"))),
            label3,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstString(factory.createString("r8")),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.stringType,
                    factory.createString("logcatTag"))),
            label4,
            new CfReturnVoid(),
            label5),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode2_addLine(DexItemFactory factory, DexMethod method) {
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
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.stringBuilderType,
                    factory.createString("builder"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.stringType),
                    factory.createString("append")),
                false),
            new CfConstNumber(10, ValueType.INT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charType),
                    factory.createString("append")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode3_addNonSyntheticMethod(
      DexItemFactory factory, DexMethod method) {
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
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                    factory.createString("getInstance")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("addLine")),
                false),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode4_addSyntheticMethod(DexItemFactory factory, DexMethod method) {
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
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                    factory.createString("getInstance")),
                false),
            new CfNew(factory.stringBuilderType),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfConstNumber(83, ValueType.INT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringBuilderType, factory.charType),
                    factory.createString("append")),
                false),
            new CfLoad(ValueType.OBJECT, 0),
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
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("addLine")),
                false),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode5_getInstance(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    return new CfCode(
        method.holder,
        1,
        0,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createString("INSTANCE"))),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode6_writeToFile(DexItemFactory factory, DexMethod method) {
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
            new CfNew(factory.createType("Ljava/io/FileOutputStream;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/FileOutputStream;"),
                    factory.createProto(factory.voidType, factory.createType("Ljava/io/File;")),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.stringBuilderType,
                    factory.createString("builder"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringBuilderType,
                    factory.createProto(factory.stringType),
                    factory.createString("toString")),
                false),
            new CfConstString(factory.createString("UTF-8")),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/nio/charset/Charset;"),
                    factory.createProto(
                        factory.createType("Ljava/nio/charset/Charset;"), factory.stringType),
                    factory.createString("forName")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.stringType,
                    factory.createProto(
                        factory.byteArrayType, factory.createType("Ljava/nio/charset/Charset;")),
                    factory.createString("getBytes")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileOutputStream;"),
                    factory.createProto(factory.voidType, factory.byteArrayType),
                    factory.createString("write")),
                false),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileOutputStream;"),
                    factory.createProto(factory.voidType),
                    factory.createString("close")),
                false),
            label3,
            new CfGoto(label6),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/FileOutputStream;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/FileOutputStream;"),
                    factory.createProto(factory.voidType),
                    factory.createString("close")),
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
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/FileOutputStream;"))
                    })),
            new CfReturnVoid(),
            label7),
        ImmutableList.of(
            new CfTryCatch(
                label1, label2, ImmutableList.of(factory.throwableType), ImmutableList.of(label4))),
        ImmutableList.of());
  }

  public static CfCode createCfCode7_writeToLogcat(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfConstString(factory.createString("r8")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Landroid/util/Log;"),
                    factory.createProto(factory.intType, factory.stringType, factory.stringType),
                    factory.createString("v")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
