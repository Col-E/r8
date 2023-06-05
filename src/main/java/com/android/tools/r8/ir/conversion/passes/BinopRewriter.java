// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class BinopRewriter extends CodeRewriterPass<AppInfo> {

  private static final int ALL_BITS_SET = -1;

  public BinopRewriter(AppView<?> appView) {
    super(appView);
  }

  private final Map<Class<?>, BinopDescriptor> descriptors = createBinopDescriptors();

  private Map<Class<?>, BinopDescriptor> createBinopDescriptors() {
    ImmutableMap.Builder<Class<?>, BinopDescriptor> builder = ImmutableMap.builder();
    builder.put(Add.class, new BinopDescriptor(0, 0, null, null));
    builder.put(Sub.class, new BinopDescriptor(null, 0, null, null));
    builder.put(Mul.class, new BinopDescriptor(1, 1, 0, 0));
    // The following two can be improved if we handle ZeroDivide.
    builder.put(Div.class, new BinopDescriptor(null, 1, null, null));
    builder.put(Rem.class, new BinopDescriptor(null, null, null, null));
    builder.put(And.class, new BinopDescriptor(ALL_BITS_SET, ALL_BITS_SET, 0, 0));
    builder.put(Or.class, new BinopDescriptor(0, 0, ALL_BITS_SET, ALL_BITS_SET));
    builder.put(Xor.class, new BinopDescriptor(0, 0, null, null));
    builder.put(Shl.class, new BinopDescriptor(null, 0, 0, null));
    builder.put(Shr.class, new BinopDescriptor(null, 0, 0, null));
    builder.put(Ushr.class, new BinopDescriptor(null, 0, 0, null));
    return builder.build();
  }

  /**
   * A Binop descriptor describes left and right identity and absorbing element of binop. <code>
   * In a space K, for a binop *:
   * - i is left identity if for each x in K, i * x = x.
   * - i is right identity if for each x in K, x * i = x.
   * - a is left absorbing if for each x in K, a * x = a.
   * - a is right absorbing if for each x in K, x * a = a.
   * </code>
   */
  private static class BinopDescriptor {

    final Integer leftIdentity;
    final Integer rightIdentity;
    final Integer leftAbsorbing;
    final Integer rightAbsorbing;

    private BinopDescriptor(
        Integer leftIdentity,
        Integer rightIdentity,
        Integer leftAbsorbing,
        Integer rightAbsorbing) {
      this.leftIdentity = leftIdentity;
      this.rightIdentity = rightIdentity;
      this.leftAbsorbing = leftAbsorbing;
      this.rightAbsorbing = rightAbsorbing;
    }
  }

  @Override
  String getTimingId() {
    return "BinopRewriter";
  }

  @Override
  boolean shouldRewriteCode(ProgramMethod method, IRCode code) {
    return true;
  }

  @Override
  public void rewriteCode(ProgramMethod method, IRCode code) {
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isBinop() && !next.isCmp()) {
        Binop binop = next.asBinop();
        if (binop.getNumericType() == NumericType.INT
            || binop.getNumericType() == NumericType.LONG) {
          BinopDescriptor binopDescriptor = descriptors.get(binop.getClass());
          assert binopDescriptor != null;
          ConstNumber constNumber = getConstNumber(binop.leftValue());
          if (constNumber != null) {
            if (simplify(
                binop,
                iterator,
                constNumber,
                binopDescriptor.leftIdentity,
                binop.rightValue(),
                binopDescriptor.leftAbsorbing,
                binop.leftValue())) {
              continue;
            }
          }
          constNumber = getConstNumber(binop.rightValue());
          if (constNumber != null) {
            simplify(
                binop,
                iterator,
                constNumber,
                binopDescriptor.rightIdentity,
                binop.leftValue(),
                binopDescriptor.rightAbsorbing,
                binop.rightValue());
          }
        }
      }
    }
    code.removeAllDeadAndTrivialPhis();
    assert code.isConsistentSSA(appView);
  }

  private ConstNumber getConstNumber(Value val) {
    ConstNumber constNumber = getConstNumberIfConstant(val);
    if (constNumber != null) {
      return constNumber;
    }
    // phi(v1(0), v2(0)) is equivalent to ConstNumber(0) for the simplification.
    if (val.isPhi() && getConstNumberIfConstant(val.asPhi().getOperands().get(0)) != null) {
      ConstNumber phiConstNumber = null;
      WorkList<Phi> phiWorkList = WorkList.newIdentityWorkList(val.asPhi());
      while (phiWorkList.hasNext()) {
        Phi next = phiWorkList.next();
        for (Value operand : next.getOperands()) {
          ConstNumber operandConstNumber = getConstNumberIfConstant(operand);
          if (operandConstNumber != null) {
            if (phiConstNumber == null) {
              phiConstNumber = operandConstNumber;
            } else if (operandConstNumber.getRawValue() == phiConstNumber.getRawValue()) {
              assert operandConstNumber.getOutType() == phiConstNumber.getOutType();
            } else {
              // Different const numbers, cannot conclude a value from the phi.
              return null;
            }
          } else if (operand.isPhi()) {
            phiWorkList.addIfNotSeen(operand.asPhi());
          } else {
            return null;
          }
        }
      }
      return phiConstNumber;
    }
    return null;
  }

  private static ConstNumber getConstNumberIfConstant(Value val) {
    if (val.isConstant() && val.getConstInstruction().isConstNumber()) {
      return val.getConstInstruction().asConstNumber();
    }
    return null;
  }

  private boolean simplify(
      Binop binop,
      InstructionListIterator iterator,
      ConstNumber constNumber,
      Integer identityElement,
      Value identityReplacement,
      Integer absorbingElement,
      Value absorbingReplacement) {
    int intValue;
    if (constNumber.outValue().getType().isInt()) {
      intValue = constNumber.getIntValue();
    } else {
      assert constNumber.outValue().getType().isLong();
      long longValue = constNumber.getLongValue();
      intValue = (int) longValue;
      if ((long) intValue != longValue) {
        return false;
      }
    }
    if (identityElement != null && identityElement == intValue) {
      binop.outValue().replaceUsers(identityReplacement);
      iterator.remove();
      return true;
    }
    if (absorbingElement != null && absorbingElement == intValue) {
      binop.outValue().replaceUsers(absorbingReplacement);
      iterator.remove();
      return true;
    }
    return false;
  }
}
