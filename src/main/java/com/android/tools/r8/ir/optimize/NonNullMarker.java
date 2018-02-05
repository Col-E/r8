// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Set;

public class NonNullMarker {

  public NonNullMarker() {
  }

  @VisibleForTesting
  static boolean throwsOnNullInput(Instruction instruction) {
    return (instruction.isInvokeMethodWithReceiver() && !instruction.isInvokeDirect())
        || instruction.isInstanceGet()
        || instruction.isInstancePut()
        || instruction.isArrayGet()
        || instruction.isArrayPut()
        || instruction.isArrayLength()
        || instruction.isMonitor();
  }

  private Value getNonNullInput(Instruction instruction) {
    if (instruction.isInvokeMethodWithReceiver()) {
      return instruction.asInvokeMethodWithReceiver().getReceiver();
    } else if (instruction.isInstanceGet()) {
      return instruction.asInstanceGet().object();
    } else if (instruction.isInstancePut()) {
      return instruction.asInstancePut().object();
    } else if (instruction.isArrayGet()) {
      return instruction.asArrayGet().array();
    } else if (instruction.isArrayPut()) {
      return instruction.asArrayPut().array();
    } else if (instruction.isArrayLength()) {
      return instruction.asArrayLength().array();
    } else if (instruction.isMonitor()) {
      return instruction.asMonitor().object();
    }
    throw new Unreachable("Should conform to throwsOnNullInput.");
  }

  public void addNonNull(IRCode code) {
    ListIterator<BasicBlock> blocks = code.blocks.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (!throwsOnNullInput(current)) {
          continue;
        }
        Value knownToBeNonNullValue = getNonNullInput(current);
        // Avoid adding redundant non-null instruction.
        if (knownToBeNonNullValue.isNeverNull()) {
          // Otherwise, we will have something like:
          // non_null_rcv <- non-null(rcv)
          // ...
          // another_rcv <- non-null(non_null_rcv)
          continue;
        }
        // First, if the current block has catch handler, split into two blocks, e.g.,
        //
        // ...x
        // invoke(rcv, ...)
        // ...y
        //
        //   ~>
        //
        // ...x
        // invoke(rcv, ...)
        // goto A
        //
        // A: ...y // blockWithNonNullInstruction
        //
        BasicBlock blockWithNonNullInstruction =
            block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
        // Next, add non-null fake IR, e.g.,
        // ...x
        // invoke(rcv, ...)
        // goto A
        // ...
        // A: non_null_rcv <- non-null(rcv)
        // ...y
        Value nonNullValue =
            code.createValue(ValueType.OBJECT, knownToBeNonNullValue.getLocalInfo());
        NonNull nonNull = new NonNull(nonNullValue, knownToBeNonNullValue);
        nonNull.setPosition(current.getPosition());
        if (blockWithNonNullInstruction !=  block) {
          // If we split, add non-null IR on top of the new split block.
          blockWithNonNullInstruction.listIterator().add(nonNull);
        } else {
          // Otherwise, just add it to the current block at the position of the iterator.
          iterator.add(nonNull);
        }
        // Then, replace all users of the original value that are dominated by either the current
        // block or the new split-off block. Since NPE can be explicitly caught, nullness should be
        // propagated through dominance.
        Set<Instruction> users = knownToBeNonNullValue.uniqueUsers();
        Set<Phi> phiUsers = knownToBeNonNullValue.uniquePhiUsers();
        Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
        Set<Phi> dominatedPhiUsers = Sets.newIdentityHashSet();
        DominatorTree dominatorTree = new DominatorTree(code);
        for (BasicBlock dominatee : dominatorTree.dominatedBlocks(blockWithNonNullInstruction)) {
          InstructionListIterator dominateeIterator = dominatee.listIterator();
          if (dominatee == blockWithNonNullInstruction) {
            // In the block with the inserted non null instruction, skip instructions up to and
            // including the newly inserted instruction.
            dominateeIterator.nextUntil(instruction -> instruction == nonNull);
          }
          while (dominateeIterator.hasNext()) {
            Instruction potentialUser = dominateeIterator.next();
            assert potentialUser != nonNull;
            if (users.contains(potentialUser)) {
              dominatedUsers.add(potentialUser);
            }
          }
          for (Phi phi : dominatee.getPhis()) {
            if (phiUsers.contains(phi)) {
              dominatedPhiUsers.add(phi);
            }
          }
        }
        knownToBeNonNullValue.replaceSelectiveUsers(
            nonNullValue, dominatedUsers, dominatedPhiUsers);
      }
    }
  }

  public void cleanupNonNull(IRCode code) {
    InstructionIterator it = code.instructionIterator();
    boolean needToCheckTrivialPhis = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      // non_null_rcv <- non-null(rcv)  // deleted
      // ...
      // non_null_rcv#foo
      //
      //  ~>
      //
      // rcv#foo
      if (instruction.isNonNull()) {
        NonNull nonNull = instruction.asNonNull();
        Value src = nonNull.src();
        Value dest = nonNull.dest();
        needToCheckTrivialPhis = needToCheckTrivialPhis || dest.uniquePhiUsers().size() != 0;
        dest.replaceUsers(src);
        it.remove();
      }
    }
    // non-null might introduce a phi, e.g.,
    // non_null_rcv <- non-null(rcv)
    // ...
    // v <- phi(rcv, non_null_rcv)
    //
    // Cleaning up that non-null may result in a trivial phi:
    // v <- phi(rcv, rcv)
    if (needToCheckTrivialPhis) {
      code.removeAllTrivialPhis();
    }
  }

}
