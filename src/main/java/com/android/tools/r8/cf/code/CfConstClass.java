// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfConstClass extends CfInstruction {

  private final DexType type;

  public CfConstClass(DexType type) {
    this.type = type;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_CLASS_COMPARE_ID;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return type.slowCompareTo(((CfConstClass) other).type);
  }

  public DexType getType() {
    return type;
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
    visitor.visitLdcInsn(Type.getObjectType(getInternalName(graphLens, namingLens)));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  private String getInternalName(GraphLens graphLens, NamingLens namingLens) {
    DexType rewrittenType = graphLens.lookupType(type);
    switch (rewrittenType.toShorty()) {
      case '[':
      case 'L':
        return namingLens.lookupInternalName(rewrittenType);
      case 'Z':
        return "java/lang/Boolean/TYPE";
      case 'B':
        return "java/lang/Byte/TYPE";
      case 'S':
        return "java/lang/Short/TYPE";
      case 'C':
        return "java/lang/Character/TYPE";
      case 'I':
        return "java/lang/Integer/TYPE";
      case 'F':
        return "java/lang/Float/TYPE";
      case 'J':
        return "java/lang/Long/TYPE";
      case 'D':
        return "java/lang/Double/TYPE";
      default:
        throw new Unreachable("Unexpected type in const-class: " + rewrittenType);
    }
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerConstClass(type, iterator);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addConstClass(state.push(builder.appView.dexItemFactory().classType).register, type);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forConstClass(type, context);
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexType context,
      DexType returnType,
      DexItemFactory factory,
      InitClassLens initClassLens) {
    // ... →
    // ..., value
    frameBuilder.push(factory.classType);
  }
}
