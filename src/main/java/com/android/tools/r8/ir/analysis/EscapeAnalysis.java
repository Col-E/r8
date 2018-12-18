// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Escape analysis that collects instructions where the value of interest can escape from the given
 * method, i.e., can be stored outside, passed as arguments, etc.
 *
 * Note: at this point, the analysis always returns a non-empty list for values that are defined by
 * a new-instance IR, since they could escape via the corresponding direct call to `<init>()`.
 */
public class EscapeAnalysis {

  // Returns the set of instructions where the value of interest can escape from the code.
  public static Set<Instruction> escape(IRCode code, Value valueOfInterest) {
    // Sanity check: value (or its containing block) belongs to the code.
    BasicBlock block =
        valueOfInterest.isPhi()
            ? valueOfInterest.asPhi().getBlock()
            : valueOfInterest.definition.getBlock();
    assert code.blocks.contains(block);
    List<Value> arguments = code.collectArguments();

    ImmutableSet.Builder<Instruction> builder = ImmutableSet.builder();
    Set<Value> trackedValues = Sets.newIdentityHashSet();
    Deque<Value> valuesToTrack = new ArrayDeque<>();
    valuesToTrack.push(valueOfInterest);
    while (!valuesToTrack.isEmpty()) {
      Value v = valuesToTrack.poll();
      // Make sure we are not tracking values over and over again.
      if (!trackedValues.add(v)) {
        continue;
      }
      v.uniquePhiUsers().forEach(valuesToTrack::push);
      for (Instruction user : v.uniqueUsers()) {
        // Users in the same block need one more filtering.
        if (user.getBlock() == block) {
          // When the value of interest has the definition
          if (!valueOfInterest.isPhi()) {
            List<Instruction> instructions = block.getInstructions();
            // Make sure we're not considering instructions prior to the value of interest.
            if (instructions.indexOf(user) < instructions.indexOf(valueOfInterest.definition)) {
              continue;
            }
          }
        }
        if (isDirectlyEscaping(user, code.method, arguments)) {
          builder.add(user);
          continue;
        }
        // Track aliased value.
        if (user.couldIntroduceAnAlias()) {
          Value outValue = user.outValue();
          assert outValue != null;
          valuesToTrack.push(outValue);
        }
        // Track propagated values through which the value of interest can escape indirectly.
        Value propagatedValue = getPropagatedSubject(v, user);
        if (propagatedValue != null && propagatedValue != v) {
          valuesToTrack.push(propagatedValue);
        }
      }
    }
    return builder.build();
  }

  private static boolean isDirectlyEscaping(
      Instruction instr, DexEncodedMethod invocationContext, List<Value> arguments) {
    // As return value.
    if (instr.isReturn()) {
      return true;
    }
    // Throwing an exception.
    if (instr.isThrow()) {
      return true;
    }
    // Storing to the static field.
    if (instr.isStaticPut()) {
      return true;
    }
    // Passing as arguments.
    if (instr.isInvokeMethod()) {
      DexMethod invokedMethod = instr.asInvokeMethod().getInvokedMethod();
      // Filter out the recursion with exactly same arguments.
      if (invokedMethod == invocationContext.method) {
        return !instr.inValues().equals(arguments);
      }
      return true;
    }
    // Storing to the argument array.
    if (instr.isArrayPut()) {
      return instr.asArrayPut().array().isArgument();
    }
    return false;
  }

  private static Value getPropagatedSubject(Value src, Instruction instr) {
    // We may need to bind array index if we want to track array-get precisely:
    //  array-put arr, idx1, x
    //  y <- array-get arr, idx2  // y is not what we want to track.
    //  z <- array-get arr, idx1  // but, z is.
    // For now, we don't distinguish such cases, which is conservative.
    if (instr.isArrayGet()) {
      return instr.asArrayGet().dest();
    } else if (instr.isArrayPut()) {
      return instr.asArrayPut().array();
    } else if (instr.isInstancePut()) {
      return instr.asInstancePut().object();
    }
    return null;
  }

}
