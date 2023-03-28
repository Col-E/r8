// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfLogicalBinop extends CfInstruction {

  public enum Opcode {
    Shl,
    Shr,
    Ushr,
    And,
    Or,
    Xor,
  }

  private final Opcode opcode;
  private final NumericType type;

  public CfLogicalBinop(Opcode opcode, NumericType type) {
    assert opcode != null;
    assert type != null;
    assert type != NumericType.FLOAT && type != NumericType.DOUBLE;
    this.opcode = opcode;
    this.type = type;
  }

  @Override
  public int getCompareToId() {
    return getAsmOpcode();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to add.
  }

  public NumericType getType() {
    return type;
  }

  public Opcode getOpcode() {
    return opcode;
  }

  public static CfLogicalBinop fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.ISHL:
        return new CfLogicalBinop(Opcode.Shl, NumericType.INT);
      case Opcodes.LSHL:
        return new CfLogicalBinop(Opcode.Shl, NumericType.LONG);
      case Opcodes.ISHR:
        return new CfLogicalBinop(Opcode.Shr, NumericType.INT);
      case Opcodes.LSHR:
        return new CfLogicalBinop(Opcode.Shr, NumericType.LONG);
      case Opcodes.IUSHR:
        return new CfLogicalBinop(Opcode.Ushr, NumericType.INT);
      case Opcodes.LUSHR:
        return new CfLogicalBinop(Opcode.Ushr, NumericType.LONG);
      case Opcodes.IAND:
        return new CfLogicalBinop(Opcode.And, NumericType.INT);
      case Opcodes.LAND:
        return new CfLogicalBinop(Opcode.And, NumericType.LONG);
      case Opcodes.IOR:
        return new CfLogicalBinop(Opcode.Or, NumericType.INT);
      case Opcodes.LOR:
        return new CfLogicalBinop(Opcode.Or, NumericType.LONG);
      case Opcodes.IXOR:
        return new CfLogicalBinop(Opcode.Xor, NumericType.INT);
      case Opcodes.LXOR:
        return new CfLogicalBinop(Opcode.Xor, NumericType.LONG);
      default:
        throw new Unreachable("Wrong ASM opcode for CfLogicalBinop " + opcode);
    }
  }

  public int getAsmOpcode() {
    switch (opcode) {
      case Shl:
        return type.isWide() ? Opcodes.LSHL : Opcodes.ISHL;
      case Shr:
        return type.isWide() ? Opcodes.LSHR : Opcodes.ISHR;
      case Ushr:
        return type.isWide() ? Opcodes.LUSHR : Opcodes.IUSHR;
      case And:
        return type.isWide() ? Opcodes.LAND : Opcodes.IAND;
      case Or:
        return type.isWide() ? Opcodes.LOR : Opcodes.IOR;
      case Xor:
        return type.isWide() ? Opcodes.LXOR : Opcodes.IXOR;
      default:
        throw new Unreachable("CfLogicalBinop has unknown opcode " + opcode);
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
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
    visitor.visitInsn(getAsmOpcode());
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 1;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int right = state.pop().register;
    int left = state.pop().register;
    int dest = state.push(ValueType.fromNumericType(type)).register;
    switch (opcode) {
      case Shl:
        builder.addShl(type, dest, left, right);
        break;
      case Shr:
        builder.addShr(type, dest, left, right);
        break;
      case Ushr:
        builder.addUshr(type, dest, left, right);
        break;
      case And:
        builder.addAnd(type, dest, left, right);
        break;
      case Or:
        builder.addOr(type, dest, left, right);
        break;
      case Xor:
        builder.addXor(type, dest, left, right);
        break;
      default:
        throw new Unreachable("CfLogicalBinop has unknown opcode " + opcode);
    }
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forBinop();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value1, value2 →
    // ..., result
    NumericType value1Type = type;
    NumericType value2Type;
    switch (opcode) {
      case And:
      case Or:
      case Xor:
        value2Type = value1Type;
        break;
      default:
        value2Type = NumericType.INT;
    }
    return frame
        .popInitialized(appView, config, value2Type)
        .popInitialized(appView, config, value1Type)
        .push(appView, config, value1Type);
  }
}
