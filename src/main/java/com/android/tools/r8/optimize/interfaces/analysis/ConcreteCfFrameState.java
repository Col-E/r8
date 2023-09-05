// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.cf.code.CfFrame.getInitializedFrameType;
import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatActual;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfAssignability.AssignabilityResult;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.Builder;
import com.android.tools.r8.cf.code.CfFrameUtils;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.cf.code.frame.WidePrimitiveFrameType;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FunctionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class ConcreteCfFrameState extends CfFrameState {

  private final Int2ObjectAVLTreeMap<FrameType> locals;
  private final ArrayDeque<PreciseFrameType> stack;
  private int stackHeight;

  public ConcreteCfFrameState() {
    this(new Int2ObjectAVLTreeMap<>(), new ArrayDeque<>(), 0);
  }

  public ConcreteCfFrameState(
      Int2ObjectAVLTreeMap<FrameType> locals, ArrayDeque<PreciseFrameType> stack, int stackHeight) {
    assert CfFrameUtils.verifyLocals(locals);
    this.locals = locals;
    this.stack = stack;
    this.stackHeight = stackHeight;
  }

  @Override
  public CfFrameState clone() {
    return new ConcreteCfFrameState(locals.clone(), stack.clone(), stackHeight);
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteCfFrameState asConcrete() {
    return this;
  }

  @Override
  public CfFrameState check(CfAnalysisConfig config, CfFrame frame) {
    CfFrame currentFrame = CfFrame.builder().setLocals(locals).setStack(stack).build();
    AssignabilityResult assignabilityResult =
        config.getAssignability().isFrameAssignable(currentFrame, frame);
    if (assignabilityResult.isFailed()) {
      return error(assignabilityResult.asFailed().getMessage());
    }
    if (config.isStrengthenFramesEnabled()) {
      return this;
    }
    CfFrame frameCopy = frame.mutableCopy();
    return new ConcreteCfFrameState(
        frameCopy.getMutableLocals(), frameCopy.getMutableStack(), stackHeight);
  }

  @Override
  public CfFrameState checkLocals(CfAnalysisConfig config, CfFrame frame) {
    AssignabilityResult assignabilityResult =
        config.getAssignability().isLocalsAssignable(locals, frame.getLocals());
    if (assignabilityResult.isFailed()) {
      return error(assignabilityResult.asFailed().getMessage());
    }
    return this;
  }

  @Override
  public CfFrameState checkStack(CfAnalysisConfig config, CfFrame frame) {
    AssignabilityResult assignabilityResult =
        config.getAssignability().isStackAssignable(stack, frame.getStack());
    if (assignabilityResult.isFailed()) {
      return error(assignabilityResult.asFailed().getMessage());
    }
    return this;
  }

  @Override
  public CfFrameState clear() {
    return bottom();
  }

  @Override
  public CfFrameState markInitialized(
      UninitializedFrameType uninitializedType, DexType initializedType) {
    if (uninitializedType.isInitialized()) {
      return error("Unexpected attempt to initialize already initialized type");
    }
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      FrameType frameType = entry.getValue();
      if (frameType.isUninitialized()) {
        entry.setValue(
            getInitializedFrameType(
                uninitializedType, frameType.asUninitialized(), initializedType));
      }
    }
    // TODO(b/214496607): By using a collection that supports element replacement this could mutate
    //  the existing stack instead of building a new one.
    ArrayDeque<PreciseFrameType> newStack = new ArrayDeque<>();
    for (PreciseFrameType frameType : stack) {
      newStack.addLast(
          frameType.isUninitialized()
              ? getInitializedFrameType(
                  uninitializedType, frameType.asUninitialized(), initializedType)
              : frameType);
    }
    return new ConcreteCfFrameState(locals, newStack, stackHeight);
  }

  public void peekStackElement(Consumer<PreciseFrameType> consumer, InternalOptions options) {
    if (!stack.isEmpty()) {
      consumer.accept(stack.peekLast());
    } else {
      assert options.getTestingOptions().allowTypeErrors;
    }
  }

  public void peekStackElements(
      int number, Consumer<Deque<PreciseFrameType>> consumer, InternalOptions options) {
    if (stack.size() >= number) {
      Deque<PreciseFrameType> result = new ArrayDeque<>(number);
      Iterator<PreciseFrameType> iterator = stack.descendingIterator();
      while (iterator.hasNext() && number > 0) {
        result.addFirst(iterator.next());
        number--;
      }
      consumer.accept(result);
    } else {
      assert options.getTestingOptions().allowTypeErrors;
    }
  }

  @Override
  public CfFrameState pop() {
    return pop(FunctionUtils::getFirst);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public CfFrameState pop(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    if (stack.isEmpty()) {
      return error("Unexpected pop from empty stack");
    }
    PreciseFrameType frameType = stack.removeLast();
    stackHeight -= frameType.getWidth();
    return fn.apply(this, frameType);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, CfAnalysisConfig config) {
    return pop(
        (state, frameType) -> {
          if (frameType.isUninitialized()) {
            if (frameType.isUninitializedThis()) {
              if (constructor.getHolderType() == config.getCurrentContext().getHolderType()
                  || config.isImmediateSuperClassOfCurrentContext(constructor.getHolderType())) {
                return state.markInitialized(
                    frameType.asUninitializedThis(), config.getCurrentContext().getHolderType());
              }
            } else if (frameType.isUninitializedNew()) {
              DexType uninitializedNewType = frameType.getUninitializedNewType();
              if (constructor.getHolderType() == uninitializedNewType) {
                return state.markInitialized(frameType.asUninitializedNew(), uninitializedNewType);
              }
            }
            return popAndInitializeConstructorMismatchError(frameType, constructor, config);
          }
          return popAndInitializeInitializedObjectError(frameType);
        });
  }

  private ErroneousCfFrameState popAndInitializeConstructorMismatchError(
      PreciseFrameType frameType, DexMethod constructor, CfAnalysisConfig config) {
    assert frameType.isUninitialized();
    StringBuilder message = new StringBuilder("Constructor mismatch, expected constructor from ");
    if (frameType.isUninitializedNew()) {
      DexType uninitializedNewType = frameType.getUninitializedNewType();
      message.append(uninitializedNewType == null ? "null" : uninitializedNewType.getTypeName());
    } else {
      assert frameType.isUninitializedThis();
      message
          .append(config.getCurrentContext().getHolderType().getTypeName())
          .append(" or its superclass");
    }
    message.append(", but was ").append(constructor.toSourceStringWithoutReturnType());
    return error(message.toString());
  }

  private ErroneousCfFrameState popAndInitializeInitializedObjectError(PreciseFrameType frameType) {
    return error("Unexpected attempt to initialize " + formatActual(frameType));
  }

  @Override
  public CfFrameState popArray(AppView<?> appView) {
    return pop(
        (state, head) ->
            isArrayTypeOrNull(head) ? state : errorUnexpectedStack(head, "an array type"));
  }

  private static boolean isArrayTypeOrNull(FrameType frameType) {
    if (frameType.isInitializedReferenceType()) {
      if (frameType.isNullType()) {
        return true;
      } else if (frameType.isInitializedNonNullReferenceTypeWithInterfaces()) {
        return frameType
            .asInitializedNonNullReferenceTypeWithInterfaces()
            .getInitializedTypeWithInterfaces()
            .isArrayType();
      } else {
        assert frameType.isInitializedNonNullReferenceTypeWithoutInterfaces();
        return frameType
            .asInitializedNonNullReferenceTypeWithoutInterfaces()
            .getInitializedType()
            .isArrayType();
      }
    }
    return false;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView,
      CfAnalysisConfig config,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    CfAssignability assignability = config.getAssignability();
    return pop(
        (state, frameType) -> {
          if (frameType.isInitialized()) {
            DexType initializedType = frameType.getInitializedType(appView.dexItemFactory());
            if (assignability.isAssignable(initializedType, expectedType)) {
              return fn.apply(state, frameType);
            }
          }
          return errorUnexpectedStack(frameType, FrameType.initialized(expectedType));
        });
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, DexType... expectedTypes) {
    CfFrameState state = this;
    for (int i = expectedTypes.length - 1; i >= 0; i--) {
      state = state.popInitialized(appView, config, expectedTypes[i]);
    }
    return state;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
    return push(config, FrameType.initialized(type));
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, TypeElement type) {
    return push(config, FrameType.initialized(type));
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, PreciseFrameType frameType) {
    int newStackHeight = stackHeight + frameType.getWidth();
    if (newStackHeight > config.getMaxStack()) {
      return pushError(config, frameType);
    }
    stack.addLast(frameType);
    stackHeight = newStackHeight;
    return this;
  }

  private ErroneousCfFrameState pushError(CfAnalysisConfig config, PreciseFrameType frameType) {
    return error(
        "The max stack height of "
            + config.getMaxStack()
            + " is violated when pushing "
            + formatActual(frameType)
            + " to existing stack of size "
            + stackHeight);
  }

  @Override
  public CfFrameState pushException(CfAnalysisConfig config, DexType guard) {
    Int2ObjectAVLTreeMap<FrameType> newLocals = new Int2ObjectAVLTreeMap<>(locals);
    ArrayDeque<PreciseFrameType> newStack = new ArrayDeque<>();
    int newStackHeight = 0;
    return new ConcreteCfFrameState(newLocals, newStack, newStackHeight)
        .push(config, FrameType.initializedNonNullReference(guard));
  }

  @Override
  public CfFrameState readLocal(
      AppView<?> appView,
      CfAnalysisConfig config,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    FrameType frameType = locals.get(localIndex);
    if (frameType == null) {
      return error("Unexpected read of missing local at index " + localIndex);
    }
    if (frameType.isInitialized()) {
      CfAssignability assignability = config.getAssignability();
      DexType actualType = frameType.getInitializedType(appView.dexItemFactory());
      if (assignability.isAssignable(actualType, expectedType)) {
        return fn.apply(this, frameType);
      }
    } else if (frameType.isUninitialized() && expectedType.isObject()) {
      return fn.apply(this, frameType);
    }
    return errorUnexpectedLocal(frameType, expectedType, localIndex);
  }

  @Override
  public CfFrameState storeLocal(int localIndex, FrameType frameType, CfAnalysisConfig config) {
    int maxLocalIndex = localIndex + BooleanUtils.intValue(frameType.isWide());
    if (maxLocalIndex >= config.getMaxLocals()) {
      return storeLocalError(localIndex, frameType, config);
    }
    CfFrameUtils.storeLocal(localIndex, frameType, locals);
    return this;
  }

  private ErroneousCfFrameState storeLocalError(
      int localIndex, FrameType frameType, CfAnalysisConfig config) {
    StringBuilder message =
        new StringBuilder("The max locals of ")
            .append(config.getMaxLocals())
            .append(" is violated when storing ")
            .append(formatActual(frameType))
            .append(" at local index ")
            .append(localIndex);
    if (frameType.isWide()) {
      message.append(" and ").append(localIndex + 1);
    }
    return error(message.toString());
  }

  public CfFrameState join(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ConcreteCfFrameState state,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    CfFrame.Builder builder = CfFrame.builder();
    joinLocals(appView, state.locals, builder, joinWithMissingLocal);
    ErroneousCfFrameState error = joinStack(appView, state.stack, builder);
    if (error != null) {
      return error;
    }
    CfFrame frame = builder.buildMutable();
    return new ConcreteCfFrameState(frame.getMutableLocals(), frame.getMutableStack(), stackHeight);
  }

  private void joinLocals(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Int2ObjectSortedMap<FrameType> locals,
      Builder builder,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    ObjectBidirectionalIterator<Entry<FrameType>> iterator =
        this.locals.int2ObjectEntrySet().iterator();
    ObjectBidirectionalIterator<Entry<FrameType>> otherIterator =
        locals.int2ObjectEntrySet().iterator();
    while (iterator.hasNext() && otherIterator.hasNext()) {
      Entry<FrameType> entry = iterator.next();
      int localIndex = entry.getIntKey();
      FrameType frameType = entry.getValue();

      Entry<FrameType> otherEntry = otherIterator.next();
      int otherLocalIndex = otherEntry.getIntKey();
      FrameType otherFrameType = otherEntry.getValue();

      if (localIndex < otherLocalIndex) {
        joinLocalsWithDifferentIndices(localIndex, otherLocalIndex, otherIterator, builder);
      } else if (otherLocalIndex < localIndex) {
        joinLocalsWithDifferentIndices(otherLocalIndex, localIndex, iterator, builder);
      } else {
        joinLocalsWithSameIndex(
            localIndex, frameType, otherFrameType, iterator, otherIterator, appView, builder);
      }
    }
    joinLocalsOnlyPresentInOne(iterator, builder, joinWithMissingLocal);
    joinLocalsOnlyPresentInOne(otherIterator, builder, joinWithMissingLocal);
  }

  private void joinLocalsWithDifferentIndices(
      int localIndex,
      int otherLocalIndex,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      CfFrame.Builder builder) {
    assert localIndex < otherLocalIndex;
    setSingleLocalToTop(localIndex, builder);
    otherIterator.previous();
  }

  private void joinLocalsWithSameIndex(
      int localIndex,
      FrameType frameType,
      FrameType otherFrameType,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CfFrame.Builder builder) {
    if (frameType.isSingle()) {
      if (otherFrameType.isSingle()) {
        joinSingleLocalsWithSameIndex(
            localIndex, frameType.asSingle(), otherFrameType.asSingle(), appView, builder);
      } else {
        joinSingleAndWideLocalsWithSameIndex(localIndex, builder);
      }
    } else {
      if (otherFrameType.isWide()) {
        joinWideLocalsWithSameIndex(
            localIndex,
            frameType.asWidePrimitive(),
            otherFrameType.asWidePrimitive(),
            iterator,
            otherIterator,
            builder);
      } else {
        joinSingleAndWideLocalsWithSameIndex(localIndex, builder);
      }
    }
  }

  private void joinSingleLocalsWithSameIndex(
      int localIndex,
      SingleFrameType frameType,
      SingleFrameType otherFrameType,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CfFrame.Builder builder) {
    builder.store(localIndex, frameType.join(appView, otherFrameType));
  }

  private void joinSingleAndWideLocalsWithSameIndex(int localIndex, CfFrame.Builder builder) {
    setSingleLocalToTop(localIndex, builder);
  }

  @SuppressWarnings("ReferenceEquality")
  private void joinWideLocalsWithSameIndex(
      int localIndex,
      WidePrimitiveFrameType frameType,
      WidePrimitiveFrameType otherFrameType,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      CfFrame.Builder builder) {
    if (frameType.isWidePrimitiveLow() != otherFrameType.isWidePrimitiveLow()) {
      setSingleLocalToTop(localIndex, builder);
      return;
    }
    if (frameType == otherFrameType) {
      builder.store(localIndex, frameType);
    } else {
      setWideLocalToTop(localIndex, builder);
    }
    acceptWidePrimitiveHigh(localIndex, frameType, iterator);
    acceptWidePrimitiveHigh(localIndex, otherFrameType, otherIterator);
  }

  private void acceptWidePrimitiveHigh(
      int localIndex,
      WidePrimitiveFrameType frameType,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator) {
    assert iterator.hasNext();
    Entry<FrameType> entry = iterator.next();
    int nextLocalIndex = entry.getIntKey();
    assert nextLocalIndex == localIndex + 1;
    FrameType nextFrameType = entry.getValue();
    assert nextFrameType == frameType.getHighType();
  }

  private void joinLocalsOnlyPresentInOne(
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      CfFrame.Builder builder,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    if (!iterator.hasNext()) {
      return;
    }
    Entry<FrameType> firstEntry = iterator.next();
    if (firstEntry.getValue().isWidePrimitiveHigh()) {
      setSingleLocalToTop(firstEntry.getIntKey(), builder);
    } else {
      joinLocalOnlyPresentInOne(iterator, firstEntry, builder, joinWithMissingLocal);
    }
    while (iterator.hasNext()) {
      Entry<FrameType> entry = iterator.next();
      joinLocalOnlyPresentInOne(iterator, entry, builder, joinWithMissingLocal);
    }
  }

  private void joinLocalOnlyPresentInOne(
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      Entry<FrameType> entry,
      CfFrame.Builder builder,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    int localIndex = entry.getIntKey();
    FrameType frameType = entry.getValue();
    assert !frameType.isWidePrimitiveHigh();
    if (frameType.isWidePrimitiveLow()) {
      acceptWidePrimitiveHigh(localIndex, frameType.asWidePrimitive(), iterator);
    }
    FrameType joinFrameType = joinWithMissingLocal.apply(frameType);
    assert joinFrameType.isSingle() == frameType.isSingle();
    if (joinFrameType.isOneWord() || joinFrameType.isTwoWord()) {
      setLocalToTop(localIndex, joinFrameType, builder);
    } else {
      builder.store(localIndex, joinFrameType);
    }
  }

  private void setLocalToTop(int localIndex, FrameType frameType, CfFrame.Builder builder) {
    if (frameType.isSingle()) {
      setSingleLocalToTop(localIndex, builder);
    } else {
      setWideLocalToTop(localIndex, builder);
    }
  }

  private void setSingleLocalToTop(int localIndex, CfFrame.Builder builder) {
    assert !builder.hasLocal(localIndex);
    builder.store(localIndex, FrameType.oneWord());
  }

  private void setWideLocalToTop(int localIndex, CfFrame.Builder builder) {
    assert !builder.hasLocal(localIndex);
    assert !builder.hasLocal(localIndex + 1);
    setSingleLocalToTop(localIndex, builder);
    setSingleLocalToTop(localIndex + 1, builder);
  }

  private ErroneousCfFrameState joinStack(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Deque<PreciseFrameType> stack,
      CfFrame.Builder builder) {
    Iterator<PreciseFrameType> iterator = this.stack.iterator();
    Iterator<PreciseFrameType> otherIterator = stack.iterator();
    int stackIndex = 0;
    while (iterator.hasNext() && otherIterator.hasNext()) {
      PreciseFrameType frameType = iterator.next();
      PreciseFrameType otherFrameType = otherIterator.next();
      if (frameType.isSingle() != otherFrameType.isSingle()) {
        return error(
            "Cannot join stacks, expected frame types at stack index "
                + stackIndex
                + " to have the same width, but was: "
                + formatActual(frameType)
                + " and "
                + formatActual(otherFrameType));
      }
      PreciseFrameType preciseJoin;
      if (frameType.isSingle()) {
        SingleFrameType join = frameType.asSingle().join(appView, otherFrameType.asSingle());
        if (join.isOneWord()) {
          return joinStackImpreciseJoinError(stackIndex, frameType, otherFrameType);
        }
        assert join.isPrecise();
        preciseJoin = join.asPrecise();
      } else {
        WideFrameType join = frameType.asWide().join(otherFrameType.asWide());
        if (join.isTwoWord()) {
          return joinStackImpreciseJoinError(stackIndex, frameType, otherFrameType);
        }
        assert join.isPrecise();
        preciseJoin = join.asPrecise();
      }
      builder.push(preciseJoin);
      stackIndex++;
    }
    if (iterator.hasNext() || otherIterator.hasNext()) {
      return error("Cannot join stacks of different size");
    }
    return null;
  }

  private ErroneousCfFrameState joinStackImpreciseJoinError(
      int stackIndex, PreciseFrameType first, PreciseFrameType second) {
    return error(
        "Cannot join stacks, expected frame types at stack index "
            + stackIndex
            + " to join to a precise (non-top) type, but types "
            + formatActual(first)
            + " and "
            + formatActual(second)
            + " do not");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConcreteCfFrameState that = (ConcreteCfFrameState) o;
    return locals.equals(that.locals) && Iterables.elementsEqual(stack, that.stack);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locals, stack);
  }
}
