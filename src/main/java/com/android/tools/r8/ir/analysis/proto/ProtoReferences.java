// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;

public class ProtoReferences {

  public final DexType extendableMessageType;
  public final DexType extensionRegistryLiteType;
  public final DexType generatedExtensionType;
  public final DexType generatedMessageLiteType;
  public final DexType generatedMessageLiteBuilderType;
  public final DexType rawMessageInfoType;
  public final DexType messageLiteType;
  public final DexType methodToInvokeType;

  public final GeneratedMessageLiteMethods generatedMessageLiteMethods;
  public final GeneratedMessageLiteBuilderMethods generatedMessageLiteBuilderMethods;

  public final DexString dynamicMethodName;
  public final DexString findLiteExtensionByNumberName;

  public final DexProto dynamicMethodProto;
  public final DexProto findLiteExtensionByNumberProto;

  public final DexMethod newMessageInfoMethod;
  public final DexMethod rawMessageInfoConstructor;

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
    generatedMessageLiteBuilderType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$Builder;"));
    rawMessageInfoType =
        factory.createType(factory.createString("Lcom/google/protobuf/RawMessageInfo;"));
    messageLiteType = factory.createType(factory.createString("Lcom/google/protobuf/MessageLite;"));
    methodToInvokeType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$MethodToInvoke;"));

    // Names.
    dynamicMethodName = factory.createString("dynamicMethod");
    findLiteExtensionByNumberName = factory.createString("findLiteExtensionByNumber");

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
    rawMessageInfoConstructor =
        factory.createMethod(
            rawMessageInfoType,
            factory.createProto(
                factory.voidType, messageLiteType, factory.stringType, factory.objectArrayType),
            factory.constructorMethodName);

    generatedMessageLiteMethods = new GeneratedMessageLiteMethods(factory);
    generatedMessageLiteBuilderMethods = new GeneratedMessageLiteBuilderMethods(factory);
  }

  public boolean isDynamicMethod(DexMethod method) {
    return method.name == dynamicMethodName && method.proto == dynamicMethodProto;
  }

  public boolean isDynamicMethod(DexEncodedMethod encodedMethod) {
    return isDynamicMethod(encodedMethod.method);
  }

  public boolean isFindLiteExtensionByNumberMethod(DexMethod method) {
    return method.proto == findLiteExtensionByNumberProto
        && method.name.startsWith(findLiteExtensionByNumberName);
  }

  public boolean isGeneratedMessageLiteBuilder(DexProgramClass clazz) {
    return clazz.superType == generatedMessageLiteBuilderType;
  }

  public boolean isMessageInfoConstructionMethod(DexMethod method) {
    return method.match(newMessageInfoMethod) || method == rawMessageInfoConstructor;
  }

  class GeneratedMessageLiteMethods {

    public final DexMethod createBuilderMethod;
    public final DexMethod isInitializedMethod;

    private GeneratedMessageLiteMethods(DexItemFactory dexItemFactory) {
      createBuilderMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteType,
              dexItemFactory.createProto(generatedMessageLiteBuilderType),
              "createBuilder");
      isInitializedMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteType,
              dexItemFactory.createProto(dexItemFactory.booleanType),
              "isInitialized");
    }
  }

  class GeneratedMessageLiteBuilderMethods {

    public final DexMethod buildPartialMethod;

    private GeneratedMessageLiteBuilderMethods(DexItemFactory dexItemFactory) {
      buildPartialMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteBuilderType,
              dexItemFactory.createProto(generatedMessageLiteType),
              "buildPartial");
    }
  }
}
