// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class TypeAnalysis implements TypeEnvironment {
  private final AppInfo appInfo;
  private final DexEncodedMethod encodedMethod;

  private final Deque<Value> worklist = new ArrayDeque<>();
  private final Map<Value, TypeLatticeElement> typeMap = Maps.newHashMap();

  public TypeAnalysis(AppInfo appInfo, DexEncodedMethod encodedMethod, IRCode code) {
    this.appInfo = appInfo;
    this.encodedMethod = encodedMethod;
    analyzeBlocks(code.topologicallySortedBlocks());
  }

  @Override
  public void analyze() {
    while (!worklist.isEmpty()) {
      analyzeValue(worklist.poll());
    }
  }

  @Override
  public void analyzeBlocks(List<BasicBlock> blocks) {
    assert worklist.isEmpty();
    for (BasicBlock block : blocks) {
      processBasicBlock(block);
    }
    analyze();
  }

  @Override
  public void enqueue(Value v) {
    assert v != null;
    if (!worklist.contains(v)) {
      worklist.add(v);
    }
  }

  private void processBasicBlock(BasicBlock block) {
    int argumentsSeen = encodedMethod.accessFlags.isStatic() ? 0 : -1;
    for (Instruction instruction : block.getInstructions()) {
      Value outValue = instruction.outValue();
      // Argument, a quasi instruction, needs to be handled specially:
      //   1) We can derive its type from the method signature; and
      //   2) It does not have an out value, so we can skip the env updating.
      if (instruction.isArgument()) {
        TypeLatticeElement derived;
        if (argumentsSeen < 0) {
          // Receiver
          derived = TypeLatticeElement.fromDexType(encodedMethod.method.holder, false);
        } else {
          DexType argType = encodedMethod.method.proto.parameters.values[argumentsSeen];
          derived = TypeLatticeElement.fromDexType(argType, true);
        }
        argumentsSeen++;
        assert outValue != null;
        updateTypeOfValue(outValue, derived);
      } else {
        if (outValue != null) {
          enqueue(outValue);
        }
      }
    }
    for (Phi phi : block.getPhis()) {
      enqueue(phi);
    }
  }

  private void analyzeValue(Value value) {
    TypeLatticeElement derived =
        value.isPhi()
            ? computePhiType(value.asPhi())
            : value.definition.evaluate(appInfo, this::getLatticeElement);
    updateTypeOfValue(value, derived);
  }

  private void updateTypeOfValue(Value value, TypeLatticeElement type) {
    TypeLatticeElement current = getLatticeElement(value);
    if (current.equals(type)) {
      return;
    }
    // TODO(b/72693244): attach type lattice directly to Value.
    setLatticeElement(value, type);
    // propagate the type change to (instruction) users if any.
    for (Instruction instruction : value.uniqueUsers()) {
      Value outValue = instruction.outValue();
      if (outValue != null) {
        enqueue(outValue);
      }
    }
    // Propagate the type change to phi users if any.
    for (Phi phi : value.uniquePhiUsers()) {
      enqueue(phi);
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

  @Override
  public DexType getRefinedReceiverType(InvokeMethodWithReceiver invoke) {
    DexType receiverType = invoke.getInvokedMethod().getHolder();
    TypeLatticeElement lattice = getLatticeElement(invoke.getReceiver());
    if (lattice.isClassTypeLatticeElement()) {
      DexType refinedType = lattice.asClassTypeLatticeElement().getClassType();
      if (refinedType.isSubtypeOf(receiverType, appInfo)) {
        return refinedType;
      }
    }
    return receiverType;
  }

  private static final TypeEnvironment DEFAULT_ENVIRONMENT = new TypeEnvironment() {
    @Override
    public void analyze() {
    }

    @Override
    public void analyzeBlocks(List<BasicBlock> blocks) {
    }

    @Override
    public void enqueue(Value value) {
    }

    @Override
    public TypeLatticeElement getLatticeElement(Value value) {
      return Top.getInstance();
    }

    @Override
    public DexType getRefinedReceiverType(InvokeMethodWithReceiver invoke) {
      return invoke.getInvokedMethod().holder;
    }
  };

  // TODO(b/72693244): By annotating type lattice to value, remove the default type env at all.
  public static TypeEnvironment getDefaultTypeEnvironment() {
    return DEFAULT_ENVIRONMENT;
  }

  @VisibleForTesting
  void forEach(BiConsumer<Value, TypeLatticeElement> consumer) {
    typeMap.forEach(consumer);
  }
}
