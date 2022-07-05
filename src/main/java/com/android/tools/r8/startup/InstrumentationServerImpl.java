// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See InstrumentationServerClassGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.startup;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.origin.Origin;
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
}
