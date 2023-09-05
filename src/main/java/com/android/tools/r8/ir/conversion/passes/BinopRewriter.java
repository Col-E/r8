// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.utils.BitUtils.ALL_BITS_SET_MASK;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class BinopRewriter extends CodeRewriterPass<AppInfo> {

  public BinopRewriter(AppView<?> appView) {
    super(appView);
  }

  private final Map<Class<?>, BinopDescriptor> descriptors = createBinopDescriptors();

  private Map<Class<?>, BinopDescriptor> createBinopDescriptors() {
    ImmutableMap.Builder<Class<?>, BinopDescriptor> builder = ImmutableMap.builder();
    builder.put(Add.class, BinopDescriptor.ADD);
    builder.put(Sub.class, BinopDescriptor.SUB);
    builder.put(Mul.class, BinopDescriptor.MUL);
    builder.put(Div.class, BinopDescriptor.DIV);
    builder.put(Rem.class, BinopDescriptor.REM);
    builder.put(And.class, BinopDescriptor.AND);
    builder.put(Or.class, BinopDescriptor.OR);
    builder.put(Xor.class, BinopDescriptor.XOR);
    builder.put(Shl.class, BinopDescriptor.SHL);
    builder.put(Shr.class, BinopDescriptor.SHR);
    builder.put(Ushr.class, BinopDescriptor.USHR);
    return builder.build();
  }

  /**
   * A Binop descriptor describes left and right identity and absorbing element of binop. <code>
   * In a space K, for a binop *:
   * - i is left identity if for each x in K, i * x = x.
   * - i is right identity if for each x in K, x * i = x.
   * - a is left absorbing if for each x in K, a * x = a.
   * - a is right absorbing if for each x in K, x * a = a.
   * In a space K, a binop * is associative if for each x,y,z in K, (x * y) * z = x * (y * z).
   * </code>
   */
  private enum BinopDescriptor {
    ADD(0, 0, null, null, true) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return Add.create(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left + right;
      }

      @Override
      long evaluate(long left, long right) {
        return left + right;
      }
    },
    SUB(null, 0, null, null, false) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return new Sub(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left - right;
      }

      @Override
      long evaluate(long left, long right) {
        return left - right;
      }
    },
    MUL(1, 1, 0, 0, true) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return Mul.create(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left * right;
      }

      @Override
      long evaluate(long left, long right) {
        return left * right;
      }
    },
    // The following two can be improved if we handle ZeroDivide.
    DIV(null, 1, null, null, false),
    REM(null, null, null, null, false),
    AND(ALL_BITS_SET_MASK, ALL_BITS_SET_MASK, 0, 0, true) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return And.create(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left & right;
      }

      @Override
      long evaluate(long left, long right) {
        return left & right;
      }
    },
    OR(0, 0, ALL_BITS_SET_MASK, ALL_BITS_SET_MASK, true) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return Or.create(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left | right;
      }

      @Override
      long evaluate(long left, long right) {
        return left | right;
      }
    },
    XOR(0, 0, null, null, true) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return Xor.create(numericType, dest, left, right);
      }

      @Override
      int evaluate(int left, int right) {
        return left ^ right;
      }

      @Override
      long evaluate(long left, long right) {
        return left ^ right;
      }
    },
    SHL(null, 0, 0, null, false) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return new Shl(numericType, dest, left, right);
      }

      @Override
      boolean isShift() {
        return true;
      }
    },
    SHR(null, 0, 0, null, false) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return new Shr(numericType, dest, left, right);
      }

      @Override
      boolean isShift() {
        return true;
      }
    },
    USHR(null, 0, 0, null, false) {
      @Override
      Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
        return new Ushr(numericType, dest, left, right);
      }

      @Override
      boolean isShift() {
        return true;
      }
    };

    final Integer leftIdentity;
    final Integer rightIdentity;
    final Integer leftAbsorbing;
    final Integer rightAbsorbing;
    final boolean associativeAndCommutative;

    BinopDescriptor(
        Integer leftIdentity,
        Integer rightIdentity,
        Integer leftAbsorbing,
        Integer rightAbsorbing,
        boolean associativeAndCommutative) {
      this.leftIdentity = leftIdentity;
      this.rightIdentity = rightIdentity;
      this.leftAbsorbing = leftAbsorbing;
      this.rightAbsorbing = rightAbsorbing;
      this.associativeAndCommutative = associativeAndCommutative;
    }

    Binop instantiate(NumericType numericType, Value dest, Value left, Value right) {
      throw new Unreachable();
    }

    int evaluate(int left, int right) {
      throw new Unreachable();
    }

    long evaluate(long left, long right) {
      throw new Unreachable();
    }

    boolean isShift() {
      return false;
    }
  }

  @Override
  protected String getTimingId() {
    return "BinopRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return options.testing.enableBinopOptimization
        && !isDebugMode(code.context())
        && code.metadata().mayHaveArithmeticOrLogicalBinop();
  }

  @Override
  public CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isBinop() && !next.isCmp()) {
        Binop binop = next.asBinop();
        if (binop.getNumericType() == NumericType.INT
            || binop.getNumericType() == NumericType.LONG) {
          BinopDescriptor binopDescriptor = descriptors.get(binop.getClass());
          assert binopDescriptor != null;
          if (identityAbsorbingSimplification(iterator, binop, binopDescriptor)) {
            hasChanged = true;
            continue;
          }
          hasChanged |= successiveSimplification(iterator, binop, binopDescriptor, code);
        }
      }
    }
    if (hasChanged) {
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean successiveSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    if (binop.outValue().hasDebugUsers()) {
      return false;
    }
    ConstNumber constBLeft = getConstNumber(binop.leftValue());
    ConstNumber constBRight = getConstNumber(binop.rightValue());
    if ((constBLeft != null && constBRight != null)
        || (constBLeft == null && constBRight == null)) {
      return false;
    }
    Value otherValue = constBLeft == null ? binop.leftValue() : binop.rightValue();
    if (otherValue.isPhi() || !otherValue.getDefinition().isBinop()) {
      return false;
    }
    Binop prevBinop = otherValue.getDefinition().asBinop();
    ConstNumber constALeft = getConstNumber(prevBinop.leftValue());
    ConstNumber constARight = getConstNumber(prevBinop.rightValue());
    if ((constALeft != null && constARight != null)
        || (constALeft == null && constARight == null)) {
      return false;
    }
    ConstNumber constB = constBLeft == null ? constBRight : constBLeft;
    ConstNumber constA = constALeft == null ? constARight : constALeft;
    Value input = constALeft == null ? prevBinop.leftValue() : prevBinop.rightValue();
    // We have two successive binops so that a,b constants, x the input and a * x * b.
    if (prevBinop.getClass() == binop.getClass()) {
      if (binopDescriptor.associativeAndCommutative) {
        // a * x * b => x * (a * b) where (a * b) is a constant.
        assert binop.isCommutative();
        Value newConst = addNewConstNumber(code, iterator, constB, constA, binopDescriptor);
        replaceBinop(iterator, code, input, newConst, binopDescriptor);
        return true;
      } else if (binopDescriptor.isShift()) {
        // x shift: a shift: b => x shift: (a + b) where a + b is a constant.
        if (constBRight != null && constARight != null) {
          Value newConst = addNewConstNumber(code, iterator, constB, constA, BinopDescriptor.ADD);
          replaceBinop(iterator, code, input, newConst, binopDescriptor);
          return true;
        }
      } else if (binop.isSub() && constBRight != null) {
        // a - x - b => (a - b) - x where (a - b) is a constant.
        // x - a - b => x - (a + b) where (a + b) is a constant.
        // We ignore b - (x - a) and b - (a - x) with constBRight != null.
        if (constARight == null) {
          Value newConst = addNewConstNumber(code, iterator, constA, constB, BinopDescriptor.SUB);
          replaceBinop(iterator, code, newConst, input, BinopDescriptor.SUB);
          return true;
        } else {
          Value newConst = addNewConstNumber(code, iterator, constB, constA, BinopDescriptor.ADD);
          replaceBinop(iterator, code, input, newConst, BinopDescriptor.SUB);
          return true;
        }
      }
    } else {
      if (binop.isSub() && prevBinop.isAdd() && constBRight != null) {
        // x + a - b => x + (a - b) where (a - b) is a constant.
        // a + x - b => x + (a - b) where (a - b) is a constant.
        // We ignore b - (x + a) and b - (a + x) with constBRight != null.
        Value newConst = addNewConstNumber(code, iterator, constA, constB, BinopDescriptor.SUB);
        replaceBinop(iterator, code, newConst, input, BinopDescriptor.ADD);
        return true;
      } else if (binop.isAdd() && prevBinop.isSub()) {
        // x - a + b => x - (a - b) where (a - b) is a constant.
        // a - x + b => (a + b) - x where (a + b) is a constant.
        if (constALeft == null) {
          Value newConst = addNewConstNumber(code, iterator, constA, constB, BinopDescriptor.SUB);
          replaceBinop(iterator, code, input, newConst, BinopDescriptor.SUB);
          return true;
        } else {
          Value newConst = addNewConstNumber(code, iterator, constB, constA, BinopDescriptor.ADD);
          replaceBinop(iterator, code, newConst, input, BinopDescriptor.SUB);
          return true;
        }
      }
    }
    return false;
  }

  private void replaceBinop(
      InstructionListIterator iterator,
      IRCode code,
      Value left,
      Value right,
      BinopDescriptor binopDescriptor) {
    Binop newBinop = instantiateBinop(code, left, right, binopDescriptor);
    iterator.replaceCurrentInstruction(newBinop);
    // We need to reset the iterator state after replaceCurrentInstruction so that Iterator#remove()
    // can work in identityAbsorbingSimplification by calling previous then next.
    iterator.previous();
    iterator.next();
    identityAbsorbingSimplification(iterator, newBinop, binopDescriptor);
  }

  private Binop instantiateBinop(IRCode code, Value left, Value right, BinopDescriptor descriptor) {
    TypeElement representative = left.getType().isInt() ? right.getType() : left.getType();
    Value newValue = code.createValue(representative);
    NumericType numericType = representative.isInt() ? NumericType.INT : NumericType.LONG;
    return descriptor.instantiate(numericType, newValue, left, right);
  }

  private Value addNewConstNumber(
      IRCode code,
      InstructionListIterator iterator,
      ConstNumber left,
      ConstNumber right,
      BinopDescriptor descriptor) {
    TypeElement representative =
        left.outValue().getType().isInt() ? right.outValue().getType() : left.outValue().getType();
    long result =
        representative.isInt()
            ? descriptor.evaluate(left.getIntValue(), right.getIntValue())
            : descriptor.evaluate(left.getLongValue(), right.getLongValue());
    iterator.previous();
    Value value =
        iterator.insertConstNumberInstruction(
            code, appView.options(), result, left.outValue().getType());
    iterator.next();
    return value;
  }

  private boolean identityAbsorbingSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor) {
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
        return true;
      }
    }
    constNumber = getConstNumber(binop.rightValue());
    if (constNumber != null) {
      return simplify(
          binop,
          iterator,
          constNumber,
          binopDescriptor.rightIdentity,
          binop.leftValue(),
          binopDescriptor.rightAbsorbing,
          binop.rightValue());
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
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
