// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;
import java.util.ListIterator;

public class Goto extends JumpInstruction {

  public Goto() {
    super();
  }

  public Goto(BasicBlock block) {
    this();
    setBlock(block);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.GOTO;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public BasicBlock getTarget() {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getSuccessors();
    assert successors.size() >= 1;
    return successors.get(successors.size() - 1);
  }

  public void setTarget(BasicBlock nextBlock) {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getMutableSuccessors();
    assert successors.size() >= 1;
    BasicBlock target = successors.get(successors.size() - 1);
    target.getMutablePredecessors().remove(getBlock());
    successors.set(successors.size() - 1, nextBlock);
    nextBlock.getMutablePredecessors().add(getBlock());
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addGoto(this);
  }

  @Override
  public int maxInValueRegister() {
    assert false : "Goto has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "Goto defines no values.";
    return 0;
  }

  @Override
  public String toString() {
    BasicBlock myBlock = getBlock();
    // Avoids BasicBlock.exit(), since it will assert when block is invalid.
    if (myBlock != null
        && !myBlock.getSuccessors().isEmpty()
        && ListUtils.last(myBlock.getInstructions()) == this) {
      return super.toString() + "block " + getTarget().getNumberAsString();
    }
    return super.toString() + "block <unknown>";
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isGoto() && other.asGoto().getTarget() == getTarget();
  }

  @Override
  public boolean isGoto() {
    return true;
  }

  @Override
  public Goto asGoto() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Nothing to do.
  }

  public boolean isTrivialGotoToTheNextBlock(IRCode code) {
    BasicBlock thisBlock = getBlock();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (thisBlock == block) {
        return blockIterator.hasNext() && blockIterator.next() == getTarget();
      }
    }
    return false;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfGoto(builder.getLabel(getTarget())), this);
  }

  @Override
  public boolean isAllowedAfterThrowingInstruction() {
    return true;
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addGoto(getTarget());
  }

  public static class Builder extends BuilderBase<Builder, Goto> {

    private BasicBlock target;

    public Builder setTarget(BasicBlock target) {
      this.target = target;
      return self();
    }

    @Override
    public Goto build() {
      return amend(new Goto(target));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
