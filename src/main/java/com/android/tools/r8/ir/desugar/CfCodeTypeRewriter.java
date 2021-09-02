// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ListUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Rewrites all references to a type in a CfCode instance to another type.
 *
 * <p>The implementation is minimal for the needs of DesugaredLibraryCustomConversionRewriter.
 */
public class CfCodeTypeRewriter {

  private final DexItemFactory factory;

  public CfCodeTypeRewriter(DexItemFactory factory) {
    this.factory = factory;
  }

  public DexEncodedMethod rewriteCfCode(
      DexEncodedMethod method, Map<DexType, DexType> replacement) {
    if (method.getCode() == null) {
      assert false;
      return null;
    }
    return DexEncodedMethod.builder(method)
        .setMethod(rewriteMethod(method.getReference(), replacement))
        .setCode(rewriteCfCode(method.getCode(), replacement))
        .build();
  }

  private Code rewriteCfCode(Code code, Map<DexType, DexType> replacement) {
    assert code.isCfCode();
    CfCode cfCode = code.asCfCode();
    List<CfInstruction> desugaredInstructions =
        ListUtils.map(
            cfCode.getInstructions(),
            instruction -> rewriteCfInstruction(instruction, replacement));
    cfCode.setInstructions(desugaredInstructions);
    return cfCode;
  }

  private CfInstruction rewriteCfInstruction(
      CfInstruction instruction, Map<DexType, DexType> replacement) {
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      DexMethod replacementMethod = rewriteMethod(cfInvoke.getMethod(), replacement);
      return cfInvoke.withMethod(replacementMethod);
    }
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      return typeInstruction.withType(rewriteType(typeInstruction.getType(), replacement));
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexField field = rewriteField(fieldInstruction.getField(), replacement);
      return fieldInstruction.withField(field);
    }
    if (instruction.isFrame()) {
      CfFrame cfFrame = instruction.asFrame();
      return rewriteFrame(cfFrame, replacement);
    }
    assert !instruction.isInvokeDynamic();
    return instruction;
  }

  private CfFrame rewriteFrame(CfFrame cfFrame, Map<DexType, DexType> replacement) {
    Int2ReferenceAVLTreeMap<FrameType> newLocals = new Int2ReferenceAVLTreeMap<>();
    cfFrame
        .getLocals()
        .forEach((i, frameType) -> newLocals.put(i, rewriteFrameType(frameType, replacement)));
    Deque<FrameType> newStack = new ArrayDeque<>();
    cfFrame
        .getStack()
        .forEach(frameType -> newStack.addLast(rewriteFrameType(frameType, replacement)));
    return new CfFrame(newLocals, newStack);
  }

  private FrameType rewriteFrameType(FrameType frameType, Map<DexType, DexType> replacement) {
    if (frameType.isInitialized()) {
      return FrameType.initialized(rewriteType(frameType.getInitializedType(), replacement));
    }
    if (frameType.isUninitializedNew()) {
      FrameType.uninitializedNew(
          frameType.getUninitializedLabel(),
          rewriteType(frameType.getUninitializedNewType(), replacement));
    }
    return frameType;
  }

  private DexType rewriteType(DexType type, Map<DexType, DexType> replacement) {
    return replacement.getOrDefault(type, type);
  }

  private DexField rewriteField(DexField field, Map<DexType, DexType> replacement) {
    DexType newHolder = rewriteType(field.holder, replacement);
    DexType newType = rewriteType(field.type, replacement);
    return factory.createField(newHolder, newType, field.name);
  }

  private DexMethod rewriteMethod(DexMethod reference, Map<DexType, DexType> replacement) {
    DexType newHolder = rewriteType(reference.holder, replacement);
    DexType newReturnType = rewriteType(reference.getReturnType(), replacement);
    DexType[] newParameters = new DexType[reference.getArity()];
    for (int i = 0; i < reference.getArity(); i++) {
      newParameters[i] = rewriteType(reference.getParameter(i), replacement);
    }
    return factory.createMethod(
        newHolder, factory.createProto(newReturnType, newParameters), reference.getName());
  }
}
