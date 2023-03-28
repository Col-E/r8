// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfArrayLoad extends CfArrayLoadOrStore {

  public CfArrayLoad(MemberType type) {
    super(type);
  }

  @Override
  public int getCompareToId() {
    return getLoadType();
  }

  private int getLoadType() {
    switch (getType()) {
      case OBJECT:
        return Opcodes.AALOAD;
      case BOOLEAN_OR_BYTE:
        return Opcodes.BALOAD;
      case CHAR:
        return Opcodes.CALOAD;
      case SHORT:
        return Opcodes.SALOAD;
      case INT:
        return Opcodes.IALOAD;
      case FLOAT:
        return Opcodes.FALOAD;
      case LONG:
        return Opcodes.LALOAD;
      case DOUBLE:
        return Opcodes.DALOAD;
      default:
        throw new Unreachable("Unexpected type " + getType());
    }
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
    visitor.visitInsn(getLoadType());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot index = state.pop();
    Slot array = state.pop();
    Slot value;
    assert array.type.isObject();
    ValueType memberType = ValueType.fromMemberType(getType());
    if (array.preciseType != null) {
      value = state.push(array.preciseType.toArrayElementType(builder.appView.dexItemFactory()));
      assert state.peek().type == memberType;
    } else {
      value = state.push(memberType);
    }
    builder.addArrayGet(getType(), value.register, array.register, index.register);
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forArrayGet();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., arrayref, index →
    // ..., value
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return frame
        .popInitialized(appView, config, dexItemFactory.intType)
        .popInitialized(
            appView,
            config,
            getExpectedArrayType(dexItemFactory),
            (state, head) -> {
              if (head.isNullType()) {
                return getType() == MemberType.OBJECT
                    ? state.push(config, FrameType.nullType())
                    : state.push(appView, config, getType());
              }
              if (head.isInitializedNonNullReferenceTypeWithInterfaces()) {
                return state.push(
                    config,
                    head.asInitializedNonNullReferenceTypeWithInterfaces()
                        .getInitializedTypeWithInterfaces()
                        .asArrayType()
                        .getMemberType());
              } else {
                assert head.isInitializedNonNullReferenceTypeWithoutInterfaces();
                return state.push(
                    config,
                    head.asInitializedNonNullReferenceTypeWithoutInterfaces()
                        .getInitializedType()
                        .toArrayElementType(dexItemFactory));
              }
            });
  }
}
