// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.IntObjConsumer;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction implements Cloneable {

  public static final Int2ObjectSortedMap<FrameType> EMPTY_LOCALS = Int2ObjectSortedMaps.emptyMap();
  public static final Deque<PreciseFrameType> EMPTY_STACK = ImmutableDeque.of();

  @Override
  public boolean isFrame() {
    return true;
  }

  @Override
  public CfFrame asFrame() {
    return this;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.FRAME_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    // The frame should be determined by the code so it should for equal iff the code is equal.
    // Thus we just require the frame to be in place.
    return CfCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to add.
  }

  private final Int2ObjectSortedMap<FrameType> locals;
  private final Deque<PreciseFrameType> stack;

  // Constructor used by CfCodePrinter.
  public CfFrame() {
    this(EMPTY_LOCALS, EMPTY_STACK);
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Int2ObjectAVLTreeMap<FrameType> locals) {
    this((Int2ObjectSortedMap<FrameType>) locals, EMPTY_STACK);
    assert !locals.isEmpty() || locals == EMPTY_LOCALS : "Should use EMPTY_LOCALS instead";
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Deque<PreciseFrameType> stack) {
    this(EMPTY_LOCALS, stack);
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Constructor used by CfCodePrinter.
  public CfFrame(Int2ObjectAVLTreeMap<FrameType> locals, Deque<PreciseFrameType> stack) {
    this((Int2ObjectSortedMap<FrameType>) locals, stack);
    assert !locals.isEmpty() || locals == EMPTY_LOCALS : "Should use EMPTY_LOCALS instead";
    assert !stack.isEmpty() || stack == EMPTY_STACK : "Should use EMPTY_STACK instead";
  }

  // Internal constructor that does not require locals to be of the type Int2ObjectAVLTreeMap.
  private CfFrame(Int2ObjectSortedMap<FrameType> locals, Deque<PreciseFrameType> stack) {
    assert CfFrameUtils.verifyLocals(locals);
    assert stack.stream().allMatch(Objects::nonNull);
    this.locals = locals;
    this.stack = stack;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public CfFrame clone() {
    return new CfFrame(locals, stack);
  }

  public CfFrame mutableCopy() {
    return new CfFrame(
        (Int2ObjectSortedMap<FrameType>) new Int2ObjectAVLTreeMap<>(locals),
        new ArrayDeque<>(stack));
  }

  public void forEachLocal(IntObjConsumer<FrameType> consumer) {
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      consumer.accept(entry.getIntKey(), entry.getValue());
    }
  }

  public Int2ObjectSortedMap<FrameType> getLocals() {
    return locals;
  }

  public Int2ObjectAVLTreeMap<FrameType> getMutableLocals() {
    assert locals instanceof Int2ObjectAVLTreeMap<?>;
    return (Int2ObjectAVLTreeMap<FrameType>) locals;
  }

  public Deque<PreciseFrameType> getStack() {
    return stack;
  }

  public ArrayDeque<PreciseFrameType> getMutableStack() {
    assert stack instanceof ArrayDeque<?>;
    return (ArrayDeque<PreciseFrameType>) stack;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    CfFrame frame = (CfFrame) obj;
    return locals.equals(frame.locals) && Iterables.elementsEqual(stack, frame.stack);
  }

  @Override
  public int hashCode() {
    // Generates a hash that is identical to Objects.hash(locals, stack[0], ..., stack[n]).
    int result = 31 + locals.hashCode();
    for (PreciseFrameType frameType : stack) {
      result = 31 * result + frameType.hashCode();
    }
    return result;
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    int stackCount = computeStackCount();
    Object[] stackTypes = computeStackTypes(stackCount, graphLens, namingLens);
    int localsCount = computeLocalsCount();
    Object[] localsTypes = computeLocalsTypes(localsCount, graphLens, namingLens);
    visitor.visitFrame(F_NEW, localsCount, localsTypes, stackCount, stackTypes);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 0;
  }

  private int computeStackCount() {
    return stack.size();
  }

  public int computeStackSize() {
    int size = 0;
    for (PreciseFrameType frameType : stack) {
      size += frameType.getWidth();
    }
    return size;
  }

  private Object[] computeStackTypes(int stackCount, GraphLens graphLens, NamingLens namingLens) {
    assert stackCount == stack.size();
    if (stackCount == 0) {
      return null;
    }
    Object[] stackTypes = new Object[stackCount];
    int index = 0;
    for (PreciseFrameType frameType : stack) {
      stackTypes[index++] = frameType.getTypeOpcode(graphLens, namingLens);
    }
    return stackTypes;
  }

  private int computeLocalsCount() {
    if (locals.isEmpty()) {
      return 0;
    }
    // Compute the size of locals. Absent indexes are denoted by a single-width element (ie, TOP).
    int maxRegister = locals.lastIntKey();
    int localsCount = 0;
    for (int i = 0; i <= maxRegister; i++) {
      localsCount++;
      FrameType type = locals.get(i);
      if (type != null && type.isWide()) {
        i++;
      }
    }
    return localsCount;
  }

  private Object[] computeLocalsTypes(int localsCount, GraphLens graphLens, NamingLens namingLens) {
    if (localsCount == 0) {
      return null;
    }
    int maxRegister = locals.lastIntKey();
    Object[] localsTypes = new Object[localsCount];
    int localIndex = 0;
    for (int i = 0; i <= maxRegister; i++) {
      FrameType type = locals.get(i);
      localsTypes[localIndex++] =
          type == null ? Opcodes.TOP : type.getTypeOpcode(graphLens, namingLens);
      if (type != null && type.isWide()) {
        i++;
      }
    }
    return localsTypes;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    for (FrameType frameType : locals.values()) {
      internalRegisterUse(registry, frameType);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
    for (FrameType frameType : stack) {
      internalRegisterUse(registry, frameType);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
  }

  private void internalRegisterUse(UseRegistry<?> registry, FrameType frameType) {
    assert !frameType.isInitializedNonNullReferenceTypeWithInterfaces();
    if (frameType.isInitializedNonNullReferenceTypeWithoutInterfaces()) {
      registry.registerTypeReference(
          frameType.asInitializedNonNullReferenceTypeWithoutInterfaces().getInitializedType());
    } else if (frameType.isUninitializedNew()) {
      registry.registerTypeReference(frameType.asUninitializedNew().getUninitializedNewType());
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Nonnull
  @Override
  public CfInstruction copy(@Nonnull Map<CfLabel, CfLabel> labelMap) {
    return clone();
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    code.setStateFromFrame(this);
  }

  @Override
  public boolean emitsIR() {
    return false;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    return frame.check(config, this);
  }

  public static PreciseFrameType getInitializedFrameType(
      UninitializedFrameType unInit, UninitializedFrameType other, DexType newType) {
    if (unInit.isUninitializedThis() && other.isUninitializedThis()) {
      return FrameType.initializedNonNullReference(newType);
    }
    if (unInit.isUninitializedNew()
        && other.isUninitializedNew()
        && unInit.getUninitializedLabel() == other.getUninitializedLabel()) {
      return FrameType.initializedNonNullReference(newType);
    }
    return other;
  }

  public CfFrame mapReferenceTypes(Function<DexType, DexType> func) {
    boolean mapped = false;
    for (int var : locals.keySet()) {
      FrameType originalType = locals.get(var);
      FrameType mappedType = originalType.map(func);
      mapped = originalType != mappedType;
      if (mapped) {
        break;
      }
    }
    if (!mapped) {
      for (PreciseFrameType frameType : stack) {
        PreciseFrameType mappedType = frameType.map(func);
        mapped = frameType != mappedType;
        if (mapped) {
          break;
        }
      }
    }
    if (!mapped) {
      return this;
    }
    Builder builder = builder();
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      FrameType frameType = entry.getValue();
      if (frameType.isWidePrimitiveHigh()) {
        // This frame type has already been written as a result of processing the previous frame
        // type.
        assert builder.getLocal(entry.getIntKey()) == frameType;
        continue;
      }
      builder.store(entry.getIntKey(), frameType.map(func));
    }
    for (PreciseFrameType frameType : stack) {
      assert !frameType.isWidePrimitiveHigh();
      builder.push(frameType.map(func));
    }
    return builder.build();
  }

  public static class Builder {

    private Int2ObjectSortedMap<FrameType> locals = EMPTY_LOCALS;
    private Deque<PreciseFrameType> stack = EMPTY_STACK;

    private boolean hasIncompleteUninitializedNew = false;
    private boolean seenStore = false;

    public Builder allocateStack(int size) {
      assert stack == EMPTY_STACK;
      if (size > 0) {
        stack = new ArrayDeque<>(size);
      }
      return this;
    }

    public Builder appendLocal(FrameType frameType) {
      // Mixing appendLocal() and store() is somewhat error prone. Catch it if we ever do it.
      assert !seenStore;
      int localIndex = locals.size();
      return internalStore(localIndex, frameType);
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public boolean hasIncompleteUninitializedNew() {
      return hasIncompleteUninitializedNew;
    }

    public Builder setHasIncompleteUninitializedNew() {
      hasIncompleteUninitializedNew = true;
      return this;
    }

    public boolean hasLocal(int localIndex) {
      return locals.containsKey(localIndex);
    }

    public FrameType getLocal(int localIndex) {
      assert hasLocal(localIndex);
      return locals.get(localIndex);
    }

    public Builder push(PreciseFrameType frameType) {
      ensureMutableStack();
      stack.addLast(frameType);
      return this;
    }

    public Builder setLocals(Int2ObjectSortedMap<FrameType> locals) {
      this.locals = locals;
      return this;
    }

    public Builder setStack(Deque<PreciseFrameType> stack) {
      this.stack = stack;
      return this;
    }

    public Builder store(int localIndex, FrameType frameType) {
      seenStore = true;
      return internalStore(localIndex, frameType);
    }

    private Builder internalStore(int localIndex, FrameType frameType) {
      assert !frameType.isTwoWord();
      Int2ObjectAVLTreeMap<FrameType> mutableLocals = ensureMutableLocals();
      CfFrameUtils.storeLocal(localIndex, frameType, mutableLocals);
      return this;
    }

    public CfFrame build() {
      return new CfFrame(locals, stack);
    }

    public CfFrame buildMutable() {
      ensureMutableLocals();
      ensureMutableStack();
      return build();
    }

    private Int2ObjectAVLTreeMap<FrameType> ensureMutableLocals() {
      if (locals == EMPTY_LOCALS) {
        locals = new Int2ObjectAVLTreeMap<>();
      }
      return (Int2ObjectAVLTreeMap<FrameType>) locals;
    }

    private void ensureMutableStack() {
      if (stack == EMPTY_STACK) {
        stack = new ArrayDeque<>();
      }
    }
  }
}
