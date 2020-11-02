// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfNewArray extends CfInstruction {

  private final DexType type;

  public CfNewArray(DexType type) {
    assert type.isArrayType();
    this.type = type;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public int getCompareToId() {
    return type.isPrimitiveArrayType() ? Opcodes.NEWARRAY : Opcodes.ANEWARRAY;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return type.slowCompareTo(((CfNewArray) other).type);
  }

  private int getPrimitiveTypeCode() {
    switch (type.descriptor.content[1]) {
      case 'Z':
        return Opcodes.T_BOOLEAN;
      case 'C':
        return Opcodes.T_CHAR;
      case 'F':
        return Opcodes.T_FLOAT;
      case 'D':
        return Opcodes.T_DOUBLE;
      case 'B':
        return Opcodes.T_BYTE;
      case 'S':
        return Opcodes.T_SHORT;
      case 'I':
        return Opcodes.T_INT;
      case 'J':
        return Opcodes.T_LONG;
      default:
        throw new Unreachable("Unexpected type for new-array: " + type);
    }
  }

  private String getElementInternalName(
      DexItemFactory dexItemFactory, GraphLens graphLens, NamingLens namingLens) {
    assert !type.isPrimitiveArrayType();
    StringBuilder renamedElementDescriptor = new StringBuilder();
    // Intentionally starting from 1 to get the element descriptor.
    int numberOfLeadingSquareBrackets = getType().getNumberOfLeadingSquareBrackets();
    for (int i = 1; i < numberOfLeadingSquareBrackets; i++) {
      renamedElementDescriptor.append("[");
    }
    DexType baseType = getType().toBaseType(dexItemFactory);
    DexType rewrittenBaseType = graphLens.lookupType(baseType);
    renamedElementDescriptor.append(
        namingLens.lookupDescriptor(rewrittenBaseType).toSourceString());
    return DescriptorUtils.descriptorToInternalName(renamedElementDescriptor.toString());
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    if (type.isPrimitiveArrayType()) {
      visitor.visitIntInsn(Opcodes.NEWARRAY, getPrimitiveTypeCode());
    } else {
      visitor.visitTypeInsn(
          Opcodes.ANEWARRAY, getElementInternalName(dexItemFactory, graphLens, namingLens));
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    if (!type.isPrimitiveArrayType()) {
      registry.registerTypeReference(type);
    }
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot size = state.pop();
    Slot push = state.push(type);
    builder.addNewArrayEmpty(push.register, size.register, type);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forNewArrayEmpty(type, context);
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ..., count →
    // ..., arrayref
    assert type.isArrayType();
    frameBuilder.popAndDiscard(factory.intType).push(type);
  }
}
