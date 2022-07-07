// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class StartupConfigurationParser<C, M, T> {

  interface MethodFactory<C, M, T> {

    M createMethod(
        C methodHolder, String methodName, List<T> methodParameterTypes, T methodReturnType);
  }

  private final Function<String, C> classFactory;
  private final MethodFactory<C, M, T> methodFactory;
  private final Function<String, T> typeFactory;

  StartupConfigurationParser(
      Function<String, C> classFactory,
      MethodFactory<C, M, T> methodFactory,
      Function<String, T> typeFactory) {
    this.classFactory = classFactory;
    this.methodFactory = methodFactory;
    this.typeFactory = typeFactory;
  }

  public static StartupConfigurationParser<DexType, DexMethod, DexType> createDexParser(
      DexItemFactory dexItemFactory) {
    return new StartupConfigurationParser<>(
        dexItemFactory::createType,
        (methodHolder, methodName, methodParameters, methodReturnType) ->
            dexItemFactory.createMethod(
                methodHolder,
                dexItemFactory.createProto(methodReturnType, methodParameters),
                dexItemFactory.createString(methodName)),
        dexItemFactory::createType);
  }

  public static StartupConfigurationParser<ClassReference, MethodReference, TypeReference>
      createReferenceParser() {
    return new StartupConfigurationParser<>(
        Reference::classFromDescriptor, Reference::method, Reference::returnTypeFromDescriptor);
  }

  public void parseLines(
      List<String> startupDescriptors,
      Consumer<? super StartupClass<C, M>> startupClassConsumer,
      Consumer<? super StartupMethod<C, M>> startupMethodConsumer,
      Consumer<String> parseErrorHandler) {
    for (String startupDescriptor : startupDescriptors) {
      if (!startupDescriptor.isEmpty()) {
        parseLine(
            startupDescriptor, startupClassConsumer, startupMethodConsumer, parseErrorHandler);
      }
    }
  }

  public void parseLine(
      String startupDescriptor,
      Consumer<? super StartupClass<C, M>> startupClassConsumer,
      Consumer<? super StartupMethod<C, M>> startupMethodConsumer,
      Consumer<String> parseErrorHandler) {
    StartupItem.Builder<C, M, ?> startupItemBuilder = StartupItem.builder();
    startupDescriptor = parseSyntheticFlag(startupDescriptor, startupItemBuilder);
    parseStartupClassOrMethod(
        startupDescriptor,
        startupItemBuilder,
        startupClassConsumer,
        startupMethodConsumer,
        parseErrorHandler);
  }

  private static String parseSyntheticFlag(
      String startupDescriptor, StartupItem.Builder<?, ?, ?> startupItemBuilder) {
    if (!startupDescriptor.isEmpty() && startupDescriptor.charAt(0) == 'S') {
      startupItemBuilder.setSynthetic();
      return startupDescriptor.substring(1);
    }
    return startupDescriptor;
  }

  private void parseStartupClassOrMethod(
      String startupDescriptor,
      StartupItem.Builder<C, M, ?> startupItemBuilder,
      Consumer<? super StartupClass<C, M>> startupClassConsumer,
      Consumer<? super StartupMethod<C, M>> startupMethodConsumer,
      Consumer<String> parseErrorHandler) {
    int arrowStartIndex = getArrowStartIndex(startupDescriptor);
    if (arrowStartIndex >= 0) {
      M startupMethod = parseStartupMethodDescriptor(startupDescriptor, arrowStartIndex);
      if (startupMethod != null) {
        startupMethodConsumer.accept(
            startupItemBuilder.setMethodReference(startupMethod).buildStartupMethod());
      } else {
        parseErrorHandler.accept(startupDescriptor);
      }
    } else {
      C startupClass = parseStartupClassDescriptor(startupDescriptor);
      if (startupClass != null) {
        startupClassConsumer.accept(
            startupItemBuilder.setClassReference(startupClass).buildStartupClass());
      } else {
        parseErrorHandler.accept(startupDescriptor);
      }
    }
  }

  private static int getArrowStartIndex(String startupDescriptor) {
    return startupDescriptor.indexOf("->");
  }

  private C parseStartupClassDescriptor(String startupClassDescriptor) {
    if (DescriptorUtils.isClassDescriptor(startupClassDescriptor)) {
      return classFactory.apply(startupClassDescriptor);
    } else {
      return null;
    }
  }

  private M parseStartupMethodDescriptor(String startupMethodDescriptor, int arrowStartIndex) {
    String classDescriptor = startupMethodDescriptor.substring(0, arrowStartIndex);
    C methodHolder = parseStartupClassDescriptor(classDescriptor);
    if (methodHolder == null) {
      return null;
    }

    int methodNameStartIndex = arrowStartIndex + 2;
    String protoWithNameDescriptor = startupMethodDescriptor.substring(methodNameStartIndex);
    int methodNameEndIndex = protoWithNameDescriptor.indexOf('(');
    if (methodNameEndIndex <= 0) {
      return null;
    }
    String methodName = protoWithNameDescriptor.substring(0, methodNameEndIndex);

    String protoDescriptor = protoWithNameDescriptor.substring(methodNameEndIndex);
    return parseStartupMethodProto(methodHolder, methodName, protoDescriptor);
  }

  private M parseStartupMethodProto(C methodHolder, String methodName, String protoDescriptor) {
    List<T> parameterTypes = new ArrayList<>();
    for (String parameterTypeDescriptor :
        DescriptorUtils.getArgumentTypeDescriptors(protoDescriptor)) {
      parameterTypes.add(typeFactory.apply(parameterTypeDescriptor));
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(protoDescriptor);
    T returnType = typeFactory.apply(returnTypeDescriptor);
    return methodFactory.createMethod(methodHolder, methodName, parameterTypes, returnType);
  }
}
