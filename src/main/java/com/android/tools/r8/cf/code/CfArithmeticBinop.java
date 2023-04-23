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
import javax.annotation.Nonnull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

public class CfArithmeticBinop extends CfInstruction {
  public static final CfArithmeticBinop IADD = new CfArithmeticBinop(Opcode.Add, NumericType.INT);
  public static final CfArithmeticBinop ISUB = new CfArithmeticBinop(Opcode.Sub, NumericType.INT);
  public static final CfArithmeticBinop IMUL = new CfArithmeticBinop(Opcode.Mul, NumericType.INT);
  public static final CfArithmeticBinop IDIV = new CfArithmeticBinop(Opcode.Div, NumericType.INT);
  public static final CfArithmeticBinop IREM = new CfArithmeticBinop(Opcode.Rem, NumericType.INT);

  public static final CfArithmeticBinop LADD = new CfArithmeticBinop(Opcode.Add, NumericType.LONG);
  public static final CfArithmeticBinop LSUB = new CfArithmeticBinop(Opcode.Sub, NumericType.LONG);
  public static final CfArithmeticBinop LMUL = new CfArithmeticBinop(Opcode.Mul, NumericType.LONG);
  public static final CfArithmeticBinop LDIV = new CfArithmeticBinop(Opcode.Div, NumericType.LONG);
  public static final CfArithmeticBinop LREM = new CfArithmeticBinop(Opcode.Rem, NumericType.LONG);

  public static final CfArithmeticBinop FADD = new CfArithmeticBinop(Opcode.Add, NumericType.FLOAT);
  public static final CfArithmeticBinop FSUB = new CfArithmeticBinop(Opcode.Sub, NumericType.FLOAT);
  public static final CfArithmeticBinop FMUL = new CfArithmeticBinop(Opcode.Mul, NumericType.FLOAT);
  public static final CfArithmeticBinop FDIV = new CfArithmeticBinop(Opcode.Div, NumericType.FLOAT);
  public static final CfArithmeticBinop FREM = new CfArithmeticBinop(Opcode.Rem, NumericType.FLOAT);

  public static final CfArithmeticBinop DADD = new CfArithmeticBinop(Opcode.Add, NumericType.DOUBLE);
  public static final CfArithmeticBinop DSUB = new CfArithmeticBinop(Opcode.Sub, NumericType.DOUBLE);
  public static final CfArithmeticBinop DMUL = new CfArithmeticBinop(Opcode.Mul, NumericType.DOUBLE);
  public static final CfArithmeticBinop DDIV = new CfArithmeticBinop(Opcode.Div, NumericType.DOUBLE);
  public static final CfArithmeticBinop DREM = new CfArithmeticBinop(Opcode.Rem, NumericType.DOUBLE);

  public enum Opcode {
    Add,
    Sub,
    Mul,
    Div,
    Rem,
  }

  private final Opcode opcode;
  private final NumericType type;

  public CfArithmeticBinop(Opcode opcode, NumericType type) {
    assert opcode != null;
    assert type != null;
    this.opcode = opcode;
    this.type = type;
  }

  @Nonnull
  public static CfArithmeticBinop operation(Opcode opcode, NumericType type) {
    switch (opcode) {
      case Add:
        switch (type) {
          case BYTE:
          case CHAR:
          case SHORT:
          case INT:
            return IADD;
          case LONG:
           return LADD;
          case FLOAT:
            return FADD;
          case DOUBLE:
            return DADD;
        }
        break;
      case Sub:
        switch (type) {
          case BYTE:
          case CHAR:
          case SHORT:
          case INT:
            return ISUB;
          case LONG:
            return LSUB;
          case FLOAT:
            return FSUB;
          case DOUBLE:
            return DSUB;
        }
        break;
      case Mul:
        switch (type) {
          case BYTE:
          case CHAR:
          case SHORT:
          case INT:
            return IMUL;
          case LONG:
            return LMUL;
          case FLOAT:
            return FMUL;
          case DOUBLE:
            return DMUL;
        }
        break;
      case Div:
        switch (type) {
          case BYTE:
          case CHAR:
          case SHORT:
          case INT:
            return IDIV;
          case LONG:
            return LDIV;
          case FLOAT:
            return FDIV;
          case DOUBLE:
            return DDIV;
        }
        break;
      case Rem:
        switch (type) {
          case BYTE:
          case CHAR:
          case SHORT:
          case INT:
            return IREM;
          case LONG:
            return LREM;
          case FLOAT:
            return FREM;
          case DOUBLE:
            return DREM;
        }
        break;
    }
    throw new Unreachable("Unsupported operation: " + opcode.name() + " - " + type.name());
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

  public Opcode getOpcode() {
    return opcode;
  }

  public NumericType getType() {
    return type;
  }

  @Nonnull
  public static CfArithmeticBinop fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.IADD: return IADD;
      case Opcodes.LADD: return LADD;
      case Opcodes.FADD: return FADD;
      case Opcodes.DADD: return DADD;
      case Opcodes.ISUB: return ISUB;
      case Opcodes.LSUB: return LSUB;
      case Opcodes.FSUB: return FSUB;
      case Opcodes.DSUB: return DSUB;
      case Opcodes.IMUL: return IMUL;
      case Opcodes.LMUL: return LMUL;
      case Opcodes.FMUL: return FMUL;
      case Opcodes.DMUL: return DMUL;
      case Opcodes.IDIV: return IDIV;
      case Opcodes.LDIV: return LDIV;
      case Opcodes.FDIV: return FDIV;
      case Opcodes.DDIV: return DDIV;
      case Opcodes.IREM: return IREM;
      case Opcodes.LREM: return LREM;
      case Opcodes.FREM: return FREM;
      case Opcodes.DREM: return DREM;
      default: throw new Unreachable("Wrong ASM opcode for CfArithmeticBinop " + opcode);
    }
  }

  public int getAsmOpcode() {
    return getAsmOpcode(opcode, type);
  }

  public static int getAsmOpcode(Opcode opcode, NumericType type) {
    int typeOffset = getAsmOpcodeTypeOffset(type);
    switch (opcode) {
      case Add: return Opcodes.IADD + typeOffset;
      case Sub: return Opcodes.ISUB + typeOffset;
      case Mul: return Opcodes.IMUL + typeOffset;
      case Div: return Opcodes.IDIV + typeOffset;
      case Rem: return Opcodes.IREM + typeOffset;
      default: throw new Unreachable("CfArithmeticBinop has unknown opcode " + opcode);
    }
  }

  private static int getAsmOpcodeTypeOffset(NumericType type) {
    switch (type) {
      case LONG:   return Opcodes.LADD - Opcodes.IADD;
      case FLOAT:  return Opcodes.FADD - Opcodes.IADD;
      case DOUBLE: return Opcodes.DADD - Opcodes.IADD;
      default: return 0;
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Nonnull
  @Override
  public CfInstruction copy(@Nonnull Map<CfLabel, CfLabel> labelMap) {
    return this;
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
  public boolean canThrow() {
    return (type != NumericType.FLOAT && type != NumericType.DOUBLE)
        && (opcode == Opcode.Div || opcode == Opcode.Rem);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    int right = state.pop().register;
    int left = state.pop().register;
    int dest = state.push(ValueType.fromNumericType(type)).register;
    switch (opcode) {
      case Add:
        builder.addAdd(type, dest, left, right);
        break;
      case Sub:
        builder.addSub(type, dest, left, right);
        break;
      case Mul:
        builder.addMul(type, dest, left, right);
        break;
      case Div:
        builder.addDiv(type, dest, left, right);
        break;
      case Rem:
        builder.addRem(type, dest, left, right);
        break;
      default:
        throw new Unreachable("CfArithmeticBinop has unknown opcode " + opcode);
    }
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forBinop();
  }

  @Override
  public CfFrameState evaluate(CfFrameState state, AppView<?> appView, CfAnalysisConfig config) {
    // ..., value1, value2 →
    // ..., result
    return state
        .popInitialized(appView, config, type)
        .popInitialized(appView, config, type)
        .push(appView, config, type);
  }
}
