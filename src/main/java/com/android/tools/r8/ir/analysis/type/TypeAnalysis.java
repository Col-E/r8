// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class TypeAnalysis implements TypeEnvironment {
  private final AppInfo appInfo;
  private final DexEncodedMethod encodedMethod;

  private final Deque<BasicBlock> worklist = new ArrayDeque<>();
  private final Map<Value, TypeLatticeElement> typeMap = Maps.newHashMap();
  private final Map<Value, Set<BasicBlock>> users = Maps.newHashMap();

  public TypeAnalysis(AppInfo appInfo, DexEncodedMethod encodedMethod, IRCode code) {
    this.appInfo = appInfo;
    this.encodedMethod = encodedMethod;
    updateBlocks(code.topologicallySortedBlocks());
  }

  public void updateBlocks(List<BasicBlock> blocks) {
    assert worklist.isEmpty();
    worklist.addAll(blocks);
    while (!worklist.isEmpty()) {
      processBasicBlock(worklist.poll());
    }
  }

  private void addToWorklist(BasicBlock block) {
    if (!worklist.contains(block)) {
      worklist.add(block);
    }
  }

  private void processBasicBlock(BasicBlock block) {
    int argumentsSeen = encodedMethod.accessFlags.isStatic() ? 0 : -1;
    for (Instruction instruction : block.getInstructions()) {
      TypeLatticeElement derived = Bottom.getInstance();
      Value outValue = instruction.outValue();
      // Argument, a quasi instruction, needs to be handled specially:
      //   1) We can derive its type from the method signature; and
      //   2) It does not have an out value, so we can skip the env updating.
      if (instruction instanceof Argument) {
        if (argumentsSeen < 0) {
          // Receiver
          derived = TypeLatticeElement.fromDexType(encodedMethod.method.holder, false);
        } else {
          DexType argType = encodedMethod.method.proto.parameters.values[argumentsSeen];
          derived = TypeLatticeElement.fromDexType(argType, true);
        }
        argumentsSeen++;
      } else {
        // Register this block as a user for in values to revisit this if in values are updated.
        instruction.inValues()
            .forEach(v -> registerAsUserOfValue(v, block, Sets.newIdentityHashSet()));
        if (outValue != null) {
          derived = instruction.evaluate(appInfo, this::getLatticeElement);
        }
      }
      if (outValue != null) {
        TypeLatticeElement current = getLatticeElement(outValue);
        if (!current.equals(derived)) {
          updateTypeOfValue(outValue, derived);
        }
      }
    }
  }

  private void registerAsUserOfValue(Value value, BasicBlock block, Set<Value> seenPhis) {
    if (value.isPhi() && seenPhis.add(value)) {
      for (Value operand : value.asPhi().getOperands()) {
        registerAsUserOfValue(operand, block, seenPhis);
      }
    } else {
      users.computeIfAbsent(value, k -> Sets.newIdentityHashSet()).add(block);
    }
  }

  private void updateTypeOfValue(Value value, TypeLatticeElement type) {
    setLatticeElement(value, type);
    // Revisit the blocks that use the value whose type binding has just been updated.
    users.getOrDefault(value, Collections.emptySet()).forEach(this::addToWorklist);
    // Propagate the type change to phi users if any.
    for (Phi phi : value.uniquePhiUsers()) {
      TypeLatticeElement phiType = computePhiType(phi);
      if (!getLatticeElement(phi).equals(phiType)) {
        updateTypeOfValue(phi, phiType);
      }
    }
  }

  private TypeLatticeElement computePhiType(Phi phi) {
    // Type of phi(v1, v2, ..., vn) is the least upper bound of all those n operands.
    return TypeLatticeElement.join(
        appInfo, phi.getOperands().stream().map(this::getLatticeElement));
  }

  private void setLatticeElement(Value value, TypeLatticeElement type) {
    typeMap.put(value, type);
  }

  @Override
  public TypeLatticeElement getLatticeElement(Value value) {
    return typeMap.getOrDefault(value, Bottom.getInstance());
  }

  @VisibleForTesting
  void forEach(BiConsumer<Value, TypeLatticeElement> consumer) {
    typeMap.forEach(consumer);
  }
}
