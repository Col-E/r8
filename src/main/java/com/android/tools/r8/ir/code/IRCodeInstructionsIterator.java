// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import java.util.ListIterator;
import java.util.NoSuchElementException;

public class IRCodeInstructionsIterator implements InstructionIterator {

  private final ListIterator<BasicBlock> blockIterator;
  private InstructionListIterator instructionIterator;

  public IRCodeInstructionsIterator(IRCode code) {
    blockIterator = code.blocks.listIterator();
    instructionIterator = blockIterator.next().listIterator();
  }

  @Override
  public boolean hasNext() {
    return instructionIterator.hasNext() || blockIterator.hasNext();
  }

  @Override
  public Instruction next() {
    if (instructionIterator.hasNext()) {
      return instructionIterator.next();
    }
    if (!blockIterator.hasNext()) {
      throw new NoSuchElementException();
    }
    instructionIterator = blockIterator.next().listIterator();
    assert instructionIterator.hasNext();
    return instructionIterator.next();
  }

  @Override
  public boolean hasPrevious() {
    return instructionIterator.hasPrevious() || blockIterator.hasPrevious();
  }

  @Override
  public Instruction previous() {
    if (instructionIterator.hasPrevious()) {
      return instructionIterator.previous();
    }
    if (!blockIterator.hasPrevious()) {
      throw new NoSuchElementException();
    }
    BasicBlock block = blockIterator.previous();
    instructionIterator = block.listIterator(block.getInstructions().size());
    assert instructionIterator.hasPrevious();
    return instructionIterator.previous();
  }

  @Override
  public int nextIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int previousIndex() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(Instruction instruction) {
    instructionIterator.add(instruction);
  }

  @Override
  public void remove() {
    instructionIterator.remove();
  }

  @Override
  public void set(Instruction instruction) {
    instructionIterator.set(instruction);
  }

  @Override
  public void replaceCurrentInstruction(Instruction newInstruction) {
    instructionIterator.replaceCurrentInstruction(newInstruction);
  }

  @Override
  public void removeOrReplaceByDebugLocalRead() {
    instructionIterator.removeOrReplaceByDebugLocalRead();
  }
}
