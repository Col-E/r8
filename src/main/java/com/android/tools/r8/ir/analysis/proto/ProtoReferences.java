// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;

public class ProtoReferences {

  public final DexType extendableMessageType;
  public final DexType extensionRegistryLiteType;
  public final DexType generatedExtensionType;
  public final DexType generatedMessageLiteType;
  public final DexType rawMessageInfoType;
  public final DexType messageLiteType;
  public final DexType methodToInvokeType;

  public final DexString dynamicMethodName;
  public final DexString findLiteExtensionByNumberName;
  public final DexString findLiteExtensionByNumber1Name;
  public final DexString findLiteExtensionByNumber2Name;

  public final DexProto dynamicMethodProto;
  public final DexProto findLiteExtensionByNumberProto;

  public final DexMethod newMessageInfoMethod;

  public ProtoReferences(DexItemFactory factory) {
    // Types.
    extendableMessageType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$ExtendableMessage;"));
    extensionRegistryLiteType =
        factory.createType(factory.createString("Lcom/google/protobuf/ExtensionRegistryLite;"));
    generatedExtensionType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$GeneratedExtension;"));
    generatedMessageLiteType =
        factory.createType(factory.createString("Lcom/google/protobuf/GeneratedMessageLite;"));
    rawMessageInfoType =
        factory.createType(factory.createString("Lcom/google/protobuf/RawMessageInfo;"));
    messageLiteType = factory.createType(factory.createString("Lcom/google/protobuf/MessageLite;"));
    methodToInvokeType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$MethodToInvoke;"));

    // Names.
    dynamicMethodName = factory.createString("dynamicMethod");
    findLiteExtensionByNumberName = factory.createString("findLiteExtensionByNumber");
    findLiteExtensionByNumber1Name = factory.createString("findLiteExtensionByNumber1");
    findLiteExtensionByNumber2Name = factory.createString("findLiteExtensionByNumber2");

    // Protos.
    dynamicMethodProto =
        factory.createProto(
            factory.objectType, methodToInvokeType, factory.objectType, factory.objectType);
    findLiteExtensionByNumberProto =
        factory.createProto(generatedExtensionType, messageLiteType, factory.intType);

    // Methods.
    newMessageInfoMethod =
        factory.createMethod(
            generatedMessageLiteType,
            factory.createProto(
                factory.objectType, messageLiteType, factory.stringType, factory.objectArrayType),
            factory.createString("newMessageInfo"));
  }

  public boolean isDynamicMethod(DexMethod method) {
    return method.name == dynamicMethodName && method.proto == dynamicMethodProto;
  }

  public boolean isFindLiteExtensionByNumberMethod(DexMethod method) {
    if (method.proto == findLiteExtensionByNumberProto) {
      assert method.name != findLiteExtensionByNumber2Name;
      return method.name == findLiteExtensionByNumberName
          || method.name == findLiteExtensionByNumber1Name;
    }
    return false;
  }
}
