// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.utils.IteratorUtils;
import java.util.ListIterator;
import java.util.function.Predicate;

public class BasicBlockIterator implements ListIterator<BasicBlock> {

  protected final IRCode code;
  protected final ListIterator<BasicBlock> listIterator;
  protected BasicBlock current;

  protected BasicBlockIterator(IRCode code) {
    this.code = code;
    this.listIterator = code.blocks.listIterator();
  }

  protected BasicBlockIterator(IRCode code, int index) {
    this.code = code;
    this.listIterator = code.blocks.listIterator(index);
  }

  public BasicBlock peekPrevious() {
    return IteratorUtils.peekPrevious(this);
  }

  public BasicBlock peekNext() {
    return IteratorUtils.peekNext(this);
  }

  @Override
  public boolean hasNext() {
    return listIterator.hasNext();
  }

  @Override
  public BasicBlock next() {
    current = listIterator.next();
    return current;
  }

  @Override
  public int nextIndex() {
    return listIterator.nextIndex();
  }

  @Override
  public boolean hasPrevious() {
    return listIterator.hasPrevious();
  }

  @Override
  public BasicBlock previous() {
    current = listIterator.previous();
    return current;
  }

  @Override
  public int previousIndex() {
    return listIterator.previousIndex();
  }

  public BasicBlock positionAfterPreviousBlock(BasicBlock previousBlock) {
    return positionAfterPreviousBlock(currentBlock -> currentBlock == previousBlock);
  }

  public BasicBlock positionAfterPreviousBlock(Predicate<BasicBlock> predicate) {
    previousUntil(predicate);
    return next();
  }

  public BasicBlock previousUntil(BasicBlock stoppingCriterion) {
    return previousUntil(block -> block == stoppingCriterion);
  }

  public BasicBlock previousUntil(Predicate<BasicBlock> predicate) {
    return IteratorUtils.previousUntil(this, predicate);
  }

  @Override
  public void add(BasicBlock block) {
    listIterator.add(block);
  }

  @Override
  public void set(BasicBlock block) {
    listIterator.set(block);
  }

  /**
   * Remove the last {@link BasicBlock} that was returned by {@link #next()} or {@link #previous()}.
   * This call can only be made once per call to {@code next} or {@code previous}.
   *
   * All instructions in the block will be completely detached from the instruction stream. Each
   * instruction will have all uses of its in-values removed. If any instructions in the block
   * produces an out-value these out values must not have any users.
   */
  @Override
  public void remove() {
    if (current == null) {
      throw new IllegalStateException();
    }
    // Remove all instructions from the block before removing the block.
    InstructionListIterator iterator = current.listIterator(code);
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      instruction.clearDebugValues();
      iterator.remove();
    }
    listIterator.remove();
    current = null;
  }
}
