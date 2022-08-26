// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile.art;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class HumanReadableARTProfileParser {

  public static HumanReadableARTProfileParser create() {
    return new HumanReadableARTProfileParser();
  }

  public void parseLines(
      Stream<String> startupDescriptors,
      StartupProfileBuilder startupProfileBuilder,
      Consumer<String> parseErrorHandler) {
    startupDescriptors.forEach(
        startupDescriptor -> {
          if (!startupDescriptor.isEmpty()) {
            parseLine(startupDescriptor, startupProfileBuilder, parseErrorHandler);
          }
        });
  }

  public void parseLine(
      String startupDescriptor,
      StartupProfileBuilder startupProfileBuilder,
      Consumer<String> parseErrorHandler) {
    BooleanBox syntheticFlag = new BooleanBox();
    startupDescriptor = parseSyntheticFlag(startupDescriptor, syntheticFlag);
    parseStartupClassOrMethod(
        startupDescriptor, startupProfileBuilder, syntheticFlag, parseErrorHandler);
  }

  private static String parseSyntheticFlag(String startupDescriptor, BooleanBox syntheticFlag) {
    if (!startupDescriptor.isEmpty() && startupDescriptor.charAt(0) == 'S') {
      syntheticFlag.set();
      return startupDescriptor.substring(1);
    }
    return startupDescriptor;
  }

  private void parseStartupClassOrMethod(
      String startupDescriptor,
      StartupProfileBuilder startupProfileBuilder,
      BooleanBox syntheticFlag,
      Consumer<String> parseErrorHandler) {
    int arrowStartIndex = getArrowStartIndex(startupDescriptor);
    if (arrowStartIndex >= 0) {
      if (syntheticFlag.isFalse()) {
        MethodReference startupMethod =
            parseStartupMethodDescriptor(startupDescriptor, arrowStartIndex);
        if (startupMethod != null) {
          startupProfileBuilder.addStartupMethod(
              startupMethodBuilder -> startupMethodBuilder.setMethodReference(startupMethod));
        } else {
          parseErrorHandler.accept(startupDescriptor);
        }
      } else {
        parseErrorHandler.accept(startupDescriptor);
      }
    } else {
      ClassReference startupClass = parseStartupClassDescriptor(startupDescriptor);
      if (startupClass != null) {
        if (syntheticFlag.isFalse()) {
          startupProfileBuilder.addStartupClass(
              startupClassBuilder -> startupClassBuilder.setClassReference(startupClass));
        } else {
          startupProfileBuilder.addSyntheticStartupMethod(
              syntheticStartupMethodBuilder ->
                  syntheticStartupMethodBuilder.setSyntheticContextReference(startupClass));
        }
      } else {
        parseErrorHandler.accept(startupDescriptor);
      }
    }
  }

  private static int getArrowStartIndex(String startupDescriptor) {
    return startupDescriptor.indexOf("->");
  }

  private ClassReference parseStartupClassDescriptor(String startupClassDescriptor) {
    if (DescriptorUtils.isClassDescriptor(startupClassDescriptor)) {
      return Reference.classFromDescriptor(startupClassDescriptor);
    } else {
      return null;
    }
  }

  private MethodReference parseStartupMethodDescriptor(
      String startupMethodDescriptor, int arrowStartIndex) {
    String classDescriptor = startupMethodDescriptor.substring(0, arrowStartIndex);
    ClassReference methodHolder = parseStartupClassDescriptor(classDescriptor);
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

  private MethodReference parseStartupMethodProto(
      ClassReference methodHolder, String methodName, String protoDescriptor) {
    List<TypeReference> parameterTypes = new ArrayList<>();
    for (String parameterTypeDescriptor :
        DescriptorUtils.getArgumentTypeDescriptors(protoDescriptor)) {
      parameterTypes.add(Reference.typeFromDescriptor(parameterTypeDescriptor));
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(protoDescriptor);
    TypeReference returnType = Reference.returnTypeFromDescriptor(returnTypeDescriptor);
    return Reference.method(methodHolder, methodName, parameterTypes, returnType);
  }
}
