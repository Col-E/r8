// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.ListIterator;
import org.objectweb.asm.MethodVisitor;

public class CfConstMethodHandle extends CfInstruction {

  private final DexMethodHandle handle;

  public CfConstMethodHandle(DexMethodHandle handle) {
    this.handle = handle;
  }

  public DexMethodHandle getHandle() {
    return handle;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_METHOD_HANDLE_COMPARE_ID;
  }

  @Override
  public int internalCompareTo(CfInstruction other, CfCompareHelper helper) {
    return handle.slowCompareTo(((CfConstMethodHandle) other).handle);
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
    DexMethodHandle rewrittenHandle =
        rewriter.rewriteDexMethodHandle(
            handle, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    visitor.visitLdcInsn(rewrittenHandle.toAsmHandle(namingLens));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerMethodHandle(handle, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }

  @Override
  public boolean canThrow() {
    // const-class and const-string* may throw in dex.
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    builder.addConstMethodHandle(
        state.push(builder.appView.dexItemFactory().methodHandleType).register, handle);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forConstMethodHandle();
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
    frameBuilder.push(factory.methodHandleType);
  }
}
