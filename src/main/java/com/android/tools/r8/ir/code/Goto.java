// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.utils.CfgPrinter;
import java.util.List;
import java.util.ListIterator;

public class Goto extends JumpInstruction {

  public Goto() {
    super(null);
  }

  public Goto(BasicBlock block) {
    this();
    setBlock(block);
  }

  public BasicBlock getTarget() {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getSuccessors();
    assert successors.size() >= 1;
    return successors.get(successors.size() - 1);
  }

  public void setTarget(BasicBlock nextBlock) {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getSuccessors();
    assert successors.size() >= 1;
    BasicBlock target = successors.get(successors.size() - 1);
    target.getPredecessors().remove(getBlock());
    successors.set(successors.size() - 1, nextBlock);
    nextBlock.getPredecessors().add(getBlock());
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
    if (getBlock() != null && !getBlock().getSuccessors().isEmpty()) {
      return super.toString() + "block " + getTarget().getNumberAsString();
    }
    return super.toString() + "block <unknown>";
  }

  @Override
  public void print(CfgPrinter printer) {
    super.print(printer);
    printer.append(" B").append(getTarget().getNumber());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isGoto() && other.asGoto().getTarget() == getTarget();
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    assert other.isGoto();
    assert false : "Not supported";
    return 0;
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
    ListIterator<BasicBlock> blockIterator = code.blocks.listIterator();
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
    builder.add(new CfGoto(builder.getLabel(getTarget())));
  }
}
