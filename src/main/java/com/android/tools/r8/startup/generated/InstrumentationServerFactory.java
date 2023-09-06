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
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
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
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.util.Collections;

public final class InstrumentationServerFactory {
  public static DexProgramClass createClass(DexItemFactory dexItemFactory) {
    return new DexProgramClass(
        dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
        Kind.CF,
        Origin.unknown(),
        ClassAccessFlags.fromCfAccessFlags(1057),
        dexItemFactory.createType("Ljava/lang/Object;"),
        DexTypeList.empty(),
        dexItemFactory.createString("InstrumentationServer.java"),
        NestHostClassAttribute.none(),
        Collections.emptyList(),
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

  @SuppressWarnings("UnusedVariable")
  private static DexEncodedField[] createInstanceFields(DexItemFactory dexItemFactory) {
    return new DexEncodedField[] {};
  }

  @SuppressWarnings("UnusedVariable")
  private static DexEncodedField[] createStaticFields(DexItemFactory dexItemFactory) {
    return new DexEncodedField[] {};
  }

  private static DexEncodedMethod[] createDirectMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(1, true))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
                  dexItemFactory.createProto(dexItemFactory.createType("V")),
                  dexItemFactory.createString("<init>")))
          .setCode(method -> createInstanceInitializerCfCode0(dexItemFactory, method))
          .build(),
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(9, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType(
                          "Lcom/android/tools/r8/startup/InstrumentationServer;")),
                  dexItemFactory.createString("getInstance")))
          .setCode(method -> createCfCode1_getInstance(dexItemFactory, method))
          .build()
    };
  }

  private static DexEncodedMethod[] createVirtualMethods(DexItemFactory dexItemFactory) {
    return new DexEncodedMethod[] {
      DexEncodedMethod.syntheticBuilder()
          .setAccessFlags(MethodAccessFlags.fromCfAccessFlags(1025, false))
          .setApiLevelForCode(ComputedApiLevel.unknown())
          .setApiLevelForDefinition(ComputedApiLevel.unknown())
          .setClassFileVersion(CfVersion.V1_8)
          .setMethod(
              dexItemFactory.createMethod(
                  dexItemFactory.createType("Lcom/android/tools/r8/startup/InstrumentationServer;"),
                  dexItemFactory.createProto(
                      dexItemFactory.createType("V"), dexItemFactory.createType("Ljava/io/File;")),
                  dexItemFactory.createString("writeToFile")))
          .build()
    };
  }

  public static CfCode createInstanceInitializerCfCode0(DexItemFactory factory, DexMethod method) {
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

  public static CfCode createCfCode1_getInstance(DexItemFactory factory, DexMethod method) {
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
                    factory.createType("Lcom/android/tools/r8/startup/InstrumentationServerImpl;"),
                    factory.createProto(
                        factory.createType(
                            "Lcom/android/tools/r8/startup/InstrumentationServerImpl;")),
                    factory.createString("getInstance")),
                false),
            new CfReturn(ValueType.OBJECT)),
        ImmutableList.of(),
        ImmutableList.of());
  }
}
