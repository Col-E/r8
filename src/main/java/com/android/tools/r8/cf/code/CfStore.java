// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import javax.annotation.Nonnull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.util.Map;

public class CfStore extends CfInstruction {
  public static final CfStore ASTORE_0 = new CfStore(ValueType.OBJECT, 0);
  public static final CfStore ASTORE_1 = new CfStore(ValueType.OBJECT, 1);
  public static final CfStore ASTORE_2 = new CfStore(ValueType.OBJECT, 2);
  public static final CfStore ASTORE_3 = new CfStore(ValueType.OBJECT, 3);
  public static final CfStore ASTORE_4 = new CfStore(ValueType.OBJECT, 4);
  public static final CfStore ASTORE_5 = new CfStore(ValueType.OBJECT, 5);
  public static final CfStore ASTORE_6 = new CfStore(ValueType.OBJECT, 6);
  public static final CfStore ASTORE_7 = new CfStore(ValueType.OBJECT, 7);
  public static final CfStore ISTORE_0 = new CfStore(ValueType.INT, 0);
  public static final CfStore ISTORE_1 = new CfStore(ValueType.INT, 1);
  public static final CfStore ISTORE_2 = new CfStore(ValueType.INT, 2);
  public static final CfStore ISTORE_3 = new CfStore(ValueType.INT, 3);
  public static final CfStore ISTORE_4 = new CfStore(ValueType.INT, 4);
  public static final CfStore ISTORE_5 = new CfStore(ValueType.INT, 5);
  public static final CfStore ISTORE_6 = new CfStore(ValueType.INT, 6);
  public static final CfStore ISTORE_7 = new CfStore(ValueType.INT, 7);
  public static final CfStore FSTORE_0 = new CfStore(ValueType.FLOAT, 0);
  public static final CfStore FSTORE_1 = new CfStore(ValueType.FLOAT, 1);
  public static final CfStore FSTORE_2 = new CfStore(ValueType.FLOAT, 2);
  public static final CfStore FSTORE_3 = new CfStore(ValueType.FLOAT, 3);
  public static final CfStore FSTORE_4 = new CfStore(ValueType.FLOAT, 4);
  public static final CfStore FSTORE_5 = new CfStore(ValueType.FLOAT, 5);
  public static final CfStore LSTORE_0 = new CfStore(ValueType.LONG, 0);
  public static final CfStore LSTORE_1 = new CfStore(ValueType.LONG, 1);
  public static final CfStore LSTORE_2 = new CfStore(ValueType.LONG, 2);
  public static final CfStore LSTORE_3 = new CfStore(ValueType.LONG, 3);
  public static final CfStore LSTORE_4 = new CfStore(ValueType.LONG, 4);
  public static final CfStore LSTORE_5 = new CfStore(ValueType.LONG, 5);
  public static final CfStore DSTORE_0 = new CfStore(ValueType.DOUBLE, 0);
  public static final CfStore DSTORE_1 = new CfStore(ValueType.DOUBLE, 1);
  public static final CfStore DSTORE_2 = new CfStore(ValueType.DOUBLE, 2);
  public static final CfStore DSTORE_3 = new CfStore(ValueType.DOUBLE, 3);
  public static final CfStore DSTORE_4 = new CfStore(ValueType.DOUBLE, 4);
  public static final CfStore DSTORE_5 = new CfStore(ValueType.DOUBLE, 5);

  private final int var;
  private final ValueType type;

  private CfStore(ValueType type, int var) {
    this.var = var;
    this.type = type;
  }

  @Nonnull
  public static CfStore store(ValueType type, int var) {
    switch (type) {
      case OBJECT: return storeObject(var);
      case INT:    return storeInt(var);
      case FLOAT:  return storeFloat(var);
      case LONG:   return storeLong(var);
      case DOUBLE: return storeDouble(var);
      default:
        throw new IllegalStateException("Unknown value type: " + type);
    }
  }

  @Nonnull
  public static CfStore storeObject(int var) {
    switch (var) {
      case 0: return ASTORE_0;
      case 1: return ASTORE_1;
      case 2: return ASTORE_2;
      case 3: return ASTORE_3;
      case 4: return ASTORE_4;
      case 5: return ASTORE_5;
      case 6: return ASTORE_6;
      case 7: return ASTORE_7;
      default:
        return new CfStore(ValueType.OBJECT, var);
    }
  }

  @Nonnull
  public static CfStore storeInt(int var) {
    switch (var) {
      case 0: return ISTORE_0;
      case 1: return ISTORE_1;
      case 2: return ISTORE_2;
      case 3: return ISTORE_3;
      case 4: return ISTORE_4;
      case 5: return ISTORE_5;
      case 6: return ISTORE_6;
      case 7: return ISTORE_7;
      default:
        return new CfStore(ValueType.INT, var);
    }
  }

  @Nonnull
  public static CfStore storeFloat(int var) {
    switch (var) {
      case 0: return FSTORE_0;
      case 1: return FSTORE_1;
      case 2: return FSTORE_2;
      case 3: return FSTORE_3;
      case 4: return FSTORE_4;
      case 5: return FSTORE_5;
      default:
        return new CfStore(ValueType.FLOAT, var);
    }
  }

  @Nonnull
  public static CfStore storeLong(int var) {
    switch (var) {
      case 0: return LSTORE_0;
      case 1: return LSTORE_1;
      case 2: return LSTORE_2;
      case 3: return LSTORE_3;
      case 4: return LSTORE_4;
      case 5: return LSTORE_5;
      default:
        return new CfStore(ValueType.LONG, var);
    }
  }

  @Nonnull
  public static CfStore storeDouble(int var) {
    switch (var) {
      case 0: return DSTORE_0;
      case 1: return DSTORE_1;
      case 2: return DSTORE_2;
      case 3: return DSTORE_3;
      case 4: return DSTORE_4;
      case 5: return DSTORE_5;
      default:
        return new CfStore(ValueType.DOUBLE, var);
    }
  }

  @Override
  public int getCompareToId() {
    return getStoreType();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return visitor.visitInt(var, other.asStore().var);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(var);
  }

  private int getStoreType() {
    switch (type) {
      case OBJECT:
        return Opcodes.ASTORE;
      case INT:
        return Opcodes.ISTORE;
      case FLOAT:
        return Opcodes.FSTORE;
      case LONG:
        return Opcodes.LSTORE;
      case DOUBLE:
        return Opcodes.DSTORE;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public CfStore asStore() {
    return this;
  }

  @Override
  public boolean isStore() {
    return true;
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
    visitor.visitVarInsn(getStoreType(), var);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    // xstore_0 .. xstore_3, xstore or wide xstore, where x is a, i, f, l or d
    return var <= 3 ? 1 : ((var < 256) ? 2 : 4);
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

  public ValueType getType() {
    return type;
  }

  public int getLocalIndex() {
    return var;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Slot pop = state.pop();
    builder.addMove(type, state.write(var, pop).register, pop.register);
  }

  @Override
  public boolean emitsIR() {
    return false;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forStore();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., ref →
    // ...
    if (type.isObject()) {
      return frame.popObject((state, head) -> state.storeLocal(getLocalIndex(), head, config));
    } else {
      assert type.isPrimitive();
      return frame.popInitialized(
          appView,
          config,
          type,
          (state, head) ->
              state.storeLocal(getLocalIndex(), type.toPrimitiveType(), appView, config));
    }
  }
}
