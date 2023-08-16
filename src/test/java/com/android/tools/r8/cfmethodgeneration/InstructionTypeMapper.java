// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import java.util.Map;
import java.util.function.Function;

public class InstructionTypeMapper {

  private final DexItemFactory factory;
  private final Map<DexType, DexType> typeMap;
  private final Function<String, String> methodNameMap;

  public InstructionTypeMapper(
      DexItemFactory factory,
      Map<DexType, DexType> typeMap,
      Function<String, String> methodNameMap) {
    this.factory = factory;
    this.typeMap = typeMap;
    this.methodNameMap = methodNameMap;
  }

  public CfInstruction rewriteInstruction(CfInstruction instruction) {
    if (instruction.isTypeInstruction()) {
      CfInstruction rewritten = rewriteTypeInstruction(instruction.asTypeInstruction());
      return rewritten == null ? instruction : rewritten;
    }
    if (instruction.isFieldInstruction()) {
      return rewriteFieldInstruction(instruction.asFieldInstruction());
    }
    if (instruction.isInvoke()) {
      return rewriteInvokeInstruction(instruction.asInvoke());
    }
    if (instruction.isFrame()) {
      return rewriteFrameInstruction(instruction.asFrame());
    }
    return instruction;
  }

  private CfInstruction rewriteInvokeInstruction(CfInvoke instruction) {
    CfInvoke invoke = instruction.asInvoke();
    DexMethod method = invoke.getMethod();
    String name = method.getName().toString();
    DexType holderType = invoke.getMethod().getHolderType();
    DexType rewrittenType = typeMap.getOrDefault(holderType, holderType);
    String rewrittenName =
        rewrittenType == factory.varHandleType ? methodNameMap.apply(name) : name;
    if (rewrittenType != holderType) {
      return new CfInvoke(
          invoke.getOpcode(),
          factory.createMethod(
              rewrittenType,
              rewriteProto(invoke.getMethod().getProto()),
              factory.createString(rewrittenName)),
          invoke.isInterface());
    }
    return instruction;
  }

  private DexProto rewriteProto(DexProto proto) {
    return factory.createProto(
        typeMap.getOrDefault(proto.returnType, proto.returnType),
        proto.parameters.stream()
            .map(type -> typeMap.getOrDefault(type, type))
            .toArray(DexType[]::new));
  }

  private CfFieldInstruction rewriteFieldInstruction(CfFieldInstruction instruction) {
    DexType holderType = instruction.getField().getHolderType();
    DexType rewrittenHolderType = typeMap.getOrDefault(holderType, holderType);
    DexType fieldType = instruction.getField().getType();
    DexType rewrittenType = typeMap.getOrDefault(fieldType, fieldType);
    if (rewrittenHolderType != holderType || rewrittenType != fieldType) {
      return instruction.createWithField(
          factory.createField(rewrittenHolderType, rewrittenType, instruction.getField().name));
    }
    return instruction;
  }

  private CfInstruction rewriteTypeInstruction(CfTypeInstruction instruction) {
    DexType rewrittenType = typeMap.getOrDefault(instruction.getType(), instruction.getType());
    return rewrittenType != instruction.getType() ? instruction.withType(rewrittenType) : null;
  }

  private CfInstruction rewriteFrameInstruction(CfFrame instruction) {
    return instruction.asFrame().mapReferenceTypes(type -> typeMap.getOrDefault(type, type));
  }
}
