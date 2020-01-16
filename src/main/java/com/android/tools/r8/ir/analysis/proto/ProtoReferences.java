// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;

public class ProtoReferences {

  public final DexType extendableMessageType;
  public final DexType extensionRegistryLiteType;
  public final DexType generatedExtensionType;
  public final DexType generatedMessageLiteType;
  public final DexType generatedMessageLiteBuilderType;
  public final DexType generatedMessageLiteExtendableBuilderType;
  public final DexType rawMessageInfoType;
  public final DexType messageLiteType;
  public final DexType methodToInvokeType;

  public final GeneratedMessageLiteMethods generatedMessageLiteMethods;
  public final GeneratedMessageLiteBuilderMethods generatedMessageLiteBuilderMethods;
  public final GeneratedMessageLiteExtendableBuilderMethods
      generatedMessageLiteExtendableBuilderMethods;
  public final MethodToInvokeMembers methodToInvokeMembers;

  public final DexString dynamicMethodName;
  public final DexString findLiteExtensionByNumberName;
  public final DexString newBuilderMethodName;

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
    generatedMessageLiteExtendableBuilderType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$ExtendableBuilder;"));
    rawMessageInfoType =
        factory.createType(factory.createString("Lcom/google/protobuf/RawMessageInfo;"));
    messageLiteType = factory.createType(factory.createString("Lcom/google/protobuf/MessageLite;"));
    methodToInvokeType =
        factory.createType(
            factory.createString("Lcom/google/protobuf/GeneratedMessageLite$MethodToInvoke;"));

    // Names.
    dynamicMethodName = factory.createString("dynamicMethod");
    findLiteExtensionByNumberName = factory.createString("findLiteExtensionByNumber");
    newBuilderMethodName = factory.createString("newBuilder");

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
    generatedMessageLiteExtendableBuilderMethods =
        new GeneratedMessageLiteExtendableBuilderMethods(factory);
    methodToInvokeMembers = new MethodToInvokeMembers(factory);
  }

  public boolean isAbstractGeneratedMessageLiteBuilder(DexProgramClass clazz) {
    return clazz.type == generatedMessageLiteBuilderType
        || clazz.type == generatedMessageLiteExtendableBuilderType;
  }

  public boolean isDynamicMethod(DexMethod method) {
    return method.name == dynamicMethodName && method.proto == dynamicMethodProto;
  }

  public boolean isDynamicMethod(DexEncodedMethod encodedMethod) {
    return isDynamicMethod(encodedMethod.method);
  }

  public boolean isDynamicMethodBridge(DexMethod method) {
    return method == generatedMessageLiteMethods.dynamicMethodBridgeMethod;
  }

  public boolean isDynamicMethodBridge(DexEncodedMethod method) {
    return isDynamicMethodBridge(method.method);
  }

  public boolean isFindLiteExtensionByNumberMethod(DexMethod method) {
    return method.proto == findLiteExtensionByNumberProto
        && method.name.startsWith(findLiteExtensionByNumberName);
  }

  public boolean isGeneratedMessageLiteBuilder(DexProgramClass clazz) {
    return (clazz.superType == generatedMessageLiteBuilderType
            || clazz.superType == generatedMessageLiteExtendableBuilderType)
        && !isAbstractGeneratedMessageLiteBuilder(clazz);
  }

  public boolean isMessageInfoConstructionMethod(DexMethod method) {
    return method.match(newMessageInfoMethod) || method == rawMessageInfoConstructor;
  }

  class GeneratedMessageLiteMethods {

    public final DexMethod createBuilderMethod;
    public final DexMethod dynamicMethodBridgeMethod;
    public final DexMethod isInitializedMethod;

    private GeneratedMessageLiteMethods(DexItemFactory dexItemFactory) {
      createBuilderMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteType,
              dexItemFactory.createProto(generatedMessageLiteBuilderType),
              "createBuilder");
      dynamicMethodBridgeMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteType,
              dexItemFactory.createProto(dexItemFactory.objectType, methodToInvokeType),
              "dynamicMethod");
      isInitializedMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteType,
              dexItemFactory.createProto(dexItemFactory.booleanType),
              "isInitialized");
    }
  }

  class GeneratedMessageLiteBuilderMethods {

    public final DexMethod buildPartialMethod;
    public final DexMethod constructorMethod;

    private GeneratedMessageLiteBuilderMethods(DexItemFactory dexItemFactory) {
      buildPartialMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteBuilderType,
              dexItemFactory.createProto(generatedMessageLiteType),
              "buildPartial");
      constructorMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteBuilderType,
              dexItemFactory.createProto(dexItemFactory.voidType, generatedMessageLiteType),
              dexItemFactory.constructorMethodName);
    }
  }

  class GeneratedMessageLiteExtendableBuilderMethods {

    public final DexMethod buildPartialMethod;

    private GeneratedMessageLiteExtendableBuilderMethods(DexItemFactory dexItemFactory) {
      buildPartialMethod =
          dexItemFactory.createMethod(
              generatedMessageLiteExtendableBuilderType,
              dexItemFactory.createProto(extendableMessageType),
              "buildPartial");
    }
  }

  public class MethodToInvokeMembers {

    public final DexField buildMessageInfoField;
    public final DexField getDefaultInstanceField;
    public final DexField getMemoizedIsInitializedField;
    public final DexField getParserField;
    public final DexField newBuilderField;
    public final DexField newMutableInstanceField;
    public final DexField setMemoizedIsInitializedField;

    private MethodToInvokeMembers(DexItemFactory dexItemFactory) {
      buildMessageInfoField =
          dexItemFactory.createField(methodToInvokeType, methodToInvokeType, "BUILD_MESSAGE_INFO");
      getDefaultInstanceField =
          dexItemFactory.createField(
              methodToInvokeType, methodToInvokeType, "GET_DEFAULT_INSTANCE");
      getMemoizedIsInitializedField =
          dexItemFactory.createField(
              methodToInvokeType, methodToInvokeType, "GET_MEMOIZED_IS_INITIALIZED");
      getParserField =
          dexItemFactory.createField(methodToInvokeType, methodToInvokeType, "GET_PARSER");
      newBuilderField =
          dexItemFactory.createField(methodToInvokeType, methodToInvokeType, "NEW_BUILDER");
      newMutableInstanceField =
          dexItemFactory.createField(
              methodToInvokeType, methodToInvokeType, "NEW_MUTABLE_INSTANCE");
      setMemoizedIsInitializedField =
          dexItemFactory.createField(
              methodToInvokeType, methodToInvokeType, "SET_MEMOIZED_IS_INITIALIZED");
    }

    public boolean isNewMutableInstanceEnum(DexField field) {
      return field == newMutableInstanceField;
    }

    public boolean isNewMutableInstanceEnum(Value value) {
      Value root = value.getAliasedValue();
      return !root.isPhi()
          && root.definition.isStaticGet()
          && isNewMutableInstanceEnum(root.definition.asStaticGet().getField());
    }

    public boolean isMethodToInvokeWithSimpleBody(DexField field) {
      return field == getDefaultInstanceField
          || field == getMemoizedIsInitializedField
          || field == newBuilderField
          || field == newMutableInstanceField
          || field == setMemoizedIsInitializedField;
    }

    public boolean isMethodToInvokeWithNonSimpleBody(DexField field) {
      return field == buildMessageInfoField || field == getParserField;
    }
  }
}
