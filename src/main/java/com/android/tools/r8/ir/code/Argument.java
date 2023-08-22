// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Set;

/**
 * Argument pseudo instruction used to introduce values for all arguments for SSA conversion.
 */
public class Argument extends Instruction {

  private final int index;
  private final boolean knownToBeBoolean;

  public Argument(Value outValue, int index, boolean knownToBeBoolean) {
    super(outValue);
    this.index = index;
    this.knownToBeBoolean = knownToBeBoolean;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    // TODO(b/296996336): Should use argument optimization info of context, but the context may be
    //  the caller and not the callee during inlining.
    return AbstractValue.unknown();
  }

  public int getIndex() {
    return getIndex(true);
  }

  public int getIndexRaw() {
    return getIndex(false);
  }

  private int getIndex(boolean verifyIndex) {
    assert !verifyIndex || verifyIndex();
    return index;
  }

  private boolean verifyIndex() {
    int index = 0;
    InstructionIterator instructionIterator = getBlock().iterator();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      assert instruction.isArgument();
      if (instruction == this) {
        assert index == this.index;
        return true;
      }
      index++;
    }
    return false;
  }

  @Override
  public int opcode() {
    return Opcodes.ARGUMENT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appview, IRCode code) {
    // Never remove argument instructions. That would change the signature of the method.
    // TODO(b/65810338): If we can tell that a method never uses an argument we might be able to
    // rewrite the signature and call-sites.
    return DeadInstructionResult.notDead();
  }

  @Override
  public int maxInValueRegister() {
    assert false : "Argument has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addArgument(this);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isArgument();
  }

  @Override
  public boolean isArgument() {
    return true;
  }

  @Override
  public Argument asArgument() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forArgument();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Arguments are defined by locals so nothing to load or store.
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    throw new Unreachable();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.addArgument(this);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return outValue.getType();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return knownToBeBoolean;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  public static class Builder extends BuilderBase<Builder, Argument> {

    private int index;
    private boolean knownToBeBoolean;

    public Builder setIndex(int index) {
      this.index = index;
      return this;
    }

    @Override
    public Argument build() {
      return amend(new Argument(outValue, index, knownToBeBoolean));
    }

    @Override
    public Builder self() {
      return this;
    }
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addArgument(index, knownToBeBoolean);
  }
}
