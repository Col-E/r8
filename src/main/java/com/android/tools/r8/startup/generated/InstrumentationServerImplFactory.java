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
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
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
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MonitorType;
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
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
        Kind.CF,
        Origin.unknown(),
        ClassAccessFlags.fromCfAccessFlags(33),
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
        DexTypeList.empty(),
        dexItemFactory.createString("InstrumentationServerImpl.java"),
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
                  dexItemFactory.createType("Ljava/util/LinkedHashSet;"),
                  dexItemFactory.createString("lines")))
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
          .build(),
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType("Z"),
                  dexItemFactory.createString("writeToLogcat")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(10))
          .setApiLevel(ComputedApiLevel.unknown())
          .build(),
      DexEncodedField.syntheticBuilder()
          .setField(
              dexItemFactory.createField(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createType("Ljava/lang/String;"),
                  dexItemFactory.createString("logcatTag")))
          .setAccessFlags(FieldAccessFlags.fromCfAccessFlags(10))
          .setApiLevel(ComputedApiLevel.unknown())
          .build()
    };
  }

  private static DexEncodedMethod[] createDirectMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(2, true))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(dexItemFactory.createType("V")),
                  dexItemFactory.createString("<init>")))
          .setCode(method -> createInstanceInitializerCfCode1(dexItemFactory, method))
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
          .setCode(method -> createCfCode4_getInstance(dexItemFactory, method))
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
                  dexItemFactory.createString("addMethod")))
          .setCode(method -> createCfCode3_addMethod(dexItemFactory, method))
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
                  dexItemFactory.createString("addLine")))
          .setCode(method -> createCfCode2_addLine(dexItemFactory, method))
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
          .setCode(method -> createCfCode6_writeToLogcat(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(8, true))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType(
                      "Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                  dexItemFactory.createProto(dexItemFactory.createType("V")),
                  dexItemFactory.createString("<clinit>")))
          .setCode(method -> createClassInitializerCfCode(dexItemFactory, method))
          .build()
    };
  }

  private static DexEncodedMethod[] createVirtualMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(1, false))
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
          .setCode(method -> createCfCode5_writeToFile(dexItemFactory, method))
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
            new CfNew(factory.createType("Ljava/util/LinkedHashSet;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createProto(factory.voidType),
                    factory.createString("<init>")),
                false),
            new CfInstanceFieldWrite(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createString("lines"))),
            label2,
            new CfReturnVoid(),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode createCfCode2_addLine(DexItemFactory factory, DexMethod method) {
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
    return new CfCode(
        method.holder,
        2,
        4,
        ImmutableList.of(
            label0,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createString("lines"))),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfStore(ValueType.OBJECT, 2),
            new CfMonitor(MonitorType.ENTER),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createString("lines"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createProto(factory.booleanType, factory.objectType),
                    factory.createString("add")),
                false),
            new CfIf(IfType.NE, ValueType.INT, label4),
            label2,
            new CfLoad(ValueType.OBJECT, 2),
            new CfMonitor(MonitorType.EXIT),
            label3,
            new CfReturnVoid(),
            label4,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 2),
            new CfMonitor(MonitorType.EXIT),
            label5,
            new CfGoto(label8),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.stringType),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 3),
            new CfLoad(ValueType.OBJECT, 2),
            new CfMonitor(MonitorType.EXIT),
            label7,
            new CfLoad(ValueType.OBJECT, 3),
            new CfThrow(),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.booleanType,
                    factory.createString("writeToLogcat"))),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label9,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("writeToLogcat")),
                false),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.stringType)
                    })),
            new CfReturnVoid(),
            label11),
        ImmutableList.of(
            new CfTryCatch(
                label1, label3, ImmutableList.of(factory.throwableType), ImmutableList.of(label6)),
            new CfTryCatch(
                label4, label5, ImmutableList.of(factory.throwableType), ImmutableList.of(label6)),
            new CfTryCatch(
                label6, label7, ImmutableList.of(factory.throwableType), ImmutableList.of(label6))),
        ImmutableList.of());
  }

  public static CfCode createCfCode3_addMethod(DexItemFactory factory, DexMethod method) {
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

  public static CfCode createCfCode4_getInstance(DexItemFactory factory, DexMethod method) {
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

  public static CfCode createCfCode5_writeToFile(DexItemFactory factory, DexMethod method) {
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
        8,
        ImmutableList.of(
            label0,
            new CfNew(factory.createType("Ljava/io/PrintWriter;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfConstString(factory.createString("UTF-8")),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/io/PrintWriter;"),
                    factory.createProto(
                        factory.voidType, factory.createType("Ljava/io/File;"), factory.stringType),
                    factory.createString("<init>")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createString("lines"))),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfStore(ValueType.OBJECT, 3),
            new CfMonitor(MonitorType.ENTER),
            label2,
            new CfLoad(ValueType.OBJECT, 0),
            new CfInstanceFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createString("lines"))),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/util/LinkedHashSet;"),
                    factory.createProto(factory.createType("Ljava/util/Iterator;")),
                    factory.createString("iterator")),
                false),
            new CfStore(ValueType.OBJECT, 4),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/util/Iterator;"))
                    })),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.booleanType),
                    factory.createString("hasNext")),
                true),
            new CfIf(IfType.EQ, ValueType.INT, label6),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                185,
                factory.createMethod(
                    factory.createType("Ljava/util/Iterator;"),
                    factory.createProto(factory.objectType),
                    factory.createString("next")),
                true),
            new CfCheckCast(factory.stringType),
            new CfStore(ValueType.OBJECT, 5),
            label4,
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/PrintWriter;"),
                    factory.createProto(factory.voidType, factory.stringType),
                    factory.createString("println")),
                false),
            label5,
            new CfGoto(label3),
            label6,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;")),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 3),
            new CfMonitor(MonitorType.EXIT),
            label7,
            new CfGoto(label10),
            label8,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;")),
                      FrameType.initializedNonNullReference(factory.objectType)
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 6),
            new CfLoad(ValueType.OBJECT, 3),
            new CfMonitor(MonitorType.EXIT),
            label9,
            new CfLoad(ValueType.OBJECT, 6),
            new CfThrow(),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;"))
                    })),
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/PrintWriter;"),
                    factory.createProto(factory.voidType),
                    factory.createString("close")),
                false),
            label11,
            new CfGoto(label15),
            label12,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(FrameType.initializedNonNullReference(factory.throwableType)))),
            new CfStore(ValueType.OBJECT, 7),
            label13,
            new CfLoad(ValueType.OBJECT, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/io/PrintWriter;"),
                    factory.createProto(factory.voidType),
                    factory.createString("close")),
                false),
            label14,
            new CfLoad(ValueType.OBJECT, 7),
            new CfThrow(),
            label15,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType(
                              "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                      FrameType.initializedNonNullReference(factory.createType("Ljava/io/File;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/io/PrintWriter;"))
                    })),
            new CfReturnVoid(),
            label16),
        ImmutableList.of(
            new CfTryCatch(
                label2, label7, ImmutableList.of(factory.throwableType), ImmutableList.of(label8)),
            new CfTryCatch(
                label8, label9, ImmutableList.of(factory.throwableType), ImmutableList.of(label8)),
            new CfTryCatch(
                label1,
                label10,
                ImmutableList.of(factory.throwableType),
                ImmutableList.of(label12)),
            new CfTryCatch(
                label12,
                label13,
                ImmutableList.of(factory.throwableType),
                ImmutableList.of(label12))),
        ImmutableList.of());
  }

  public static CfCode createCfCode6_writeToLogcat(DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    return new CfCode(
        method.holder,
        2,
        2,
        ImmutableList.of(
            label0,
            new CfStaticFieldRead(
                factory.createField(
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.stringType,
                    factory.createString("logcatTag"))),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Landroid/util/Log;"),
                    factory.createProto(factory.intType, factory.stringType, factory.stringType),
                    factory.createString("i")),
                false),
            new CfStackInstruction(CfStackInstruction.Opcode.Pop),
            label1,
            new CfReturnVoid(),
            label2),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
