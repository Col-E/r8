// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.cf.code.CfFrame.getInitializedFrameType;
import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatActual;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfAssignability.AssignabilityResult;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FunctionUtils;
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
import java.util.function.UnaryOperator;

public class ConcreteCfFrameState extends CfFrameState {

  private final Int2ObjectAVLTreeMap<FrameType> locals;
  private final ArrayDeque<PreciseFrameType> stack;
  private int stackHeight;

  ConcreteCfFrameState() {
    this(new Int2ObjectAVLTreeMap<>(), new ArrayDeque<>(), 0);
  }

  public ConcreteCfFrameState(
      Int2ObjectAVLTreeMap<FrameType> locals, ArrayDeque<PreciseFrameType> stack, int stackHeight) {
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
  public CfFrameState check(AppView<?> appView, CfFrame frame) {
    CfFrame currentFrame = CfFrame.builder().setLocals(locals).setStack(stack).build();
    AssignabilityResult assignabilityResult =
        CfAssignability.isFrameAssignable(currentFrame, frame, appView);
    if (assignabilityResult.isFailed()) {
      return error(assignabilityResult.asFailed().getMessage());
    }
    CfFrame frameCopy = frame.mutableCopy();
    return new ConcreteCfFrameState(
        frameCopy.getMutableLocals(), frameCopy.getMutableStack(), stackHeight);
  }

  @Override
  public CfFrameState checkLocals(AppView<?> appView, CfFrame frame) {
    AssignabilityResult assignabilityResult =
        CfAssignability.isLocalsAssignable(locals, frame.getLocals(), appView);
    if (assignabilityResult.isFailed()) {
      return error(assignabilityResult.asFailed().getMessage());
    }
    return this;
  }

  @Override
  public CfFrameState checkStack(AppView<?> appView, CfFrame frame) {
    AssignabilityResult assignabilityResult =
        CfAssignability.isStackAssignable(stack, frame.getStack(), appView);
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

  @Override
  public CfFrameState pop() {
    return pop(FunctionUtils::getFirst);
  }

  @Override
  public CfFrameState pop(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    if (stack.isEmpty()) {
      // Return the same error as when popping from the bottom state.
      return bottom().pop();
    }
    PreciseFrameType frameType = stack.removeLast();
    stackHeight -= frameType.getWidth();
    return fn.apply(this, frameType);
  }

  @Override
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
      message.append(frameType.getUninitializedNewType().getTypeName());
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
    if (frameType.isInitializedReferenceType()
        && frameType.asInitializedReferenceType().getInitializedType().isArrayType()) {
      return true;
    }
    return frameType.isNullType();
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return pop(
        (state, frameType) -> {
          if (frameType.isInitialized()) {
            DexType initializedType = frameType.getInitializedType(appView.dexItemFactory());
            if (CfAssignability.isAssignable(initializedType, expectedType, appView)) {
              return fn.apply(state, frameType);
            }
          }
          return errorUnexpectedStack(frameType, FrameType.initialized(expectedType));
        });
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes) {
    CfFrameState state = this;
    for (int i = expectedTypes.length - 1; i >= 0; i--) {
      state = state.popInitialized(appView, expectedTypes[i]);
    }
    return state;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
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
  public CfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    FrameType frameType = locals.get(localIndex);
    if (frameType == null) {
      return error("Unexpected read of missing local at index " + localIndex);
    }
    if (frameType.isInitialized()) {
      if (CfAssignability.isAssignable(
          frameType.getInitializedType(appView.dexItemFactory()), expectedType, appView)) {
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
    locals.put(localIndex, frameType);
    if (frameType.isWide()) {
      locals.put(localIndex + 1, frameType);
    }
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
      ConcreteCfFrameState state, UnaryOperator<FrameType> joinWithMissingLocal) {
    CfFrame.Builder builder = CfFrame.builder();
    joinLocals(state.locals, builder, joinWithMissingLocal);
    ErroneousCfFrameState error = joinStack(state.stack, builder);
    if (error != null) {
      return error;
    }
    CfFrame frame = builder.buildMutable();
    return new ConcreteCfFrameState(frame.getMutableLocals(), frame.getMutableStack(), stackHeight);
  }

  private void joinLocals(
      Int2ObjectSortedMap<FrameType> locals,
      CfFrame.Builder builder,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    ObjectBidirectionalIterator<Entry<FrameType>> iterator =
        this.locals.int2ObjectEntrySet().iterator();
    ObjectBidirectionalIterator<Entry<FrameType>> otherIterator =
        locals.int2ObjectEntrySet().iterator();
    while (iterator.hasNext() && otherIterator.hasNext()) {
      Entry<FrameType> entry = nextLocal(iterator);
      int localIndex = entry.getIntKey();
      FrameType frameType = entry.getValue();

      Entry<FrameType> otherEntry = nextLocal(otherIterator);
      int otherLocalIndex = otherEntry.getIntKey();
      FrameType otherFrameType = otherEntry.getValue();

      if (localIndex < otherLocalIndex) {
        joinLocalsWithDifferentIndices(
            localIndex,
            frameType,
            otherLocalIndex,
            otherFrameType,
            iterator,
            otherIterator,
            builder);
      } else if (otherLocalIndex < localIndex) {
        joinLocalsWithDifferentIndices(
            otherLocalIndex,
            otherFrameType,
            localIndex,
            frameType,
            otherIterator,
            iterator,
            builder);
      } else {
        joinLocalsWithSameIndex(
            localIndex, frameType, otherFrameType, iterator, otherIterator, builder);
      }
    }
    joinLocalsOnlyPresentInOne(iterator, builder, joinWithMissingLocal);
    joinLocalsOnlyPresentInOne(otherIterator, builder, joinWithMissingLocal);
  }

  private void joinLocalsWithDifferentIndices(
      int localIndex,
      FrameType frameType,
      int otherLocalIndex,
      FrameType otherFrameType,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      CfFrame.Builder builder) {
    assert localIndex < otherLocalIndex;

    // Check if the smaller local does not overlap with the larger local.
    if (frameType.isSingle() || localIndex + 1 < otherLocalIndex) {
      setLocalToTop(localIndex, frameType, builder);
      previousLocal(otherIterator);
      return;
    }

    // The smaller local is a wide that overlaps with the larger local.
    setLocalToTop(localIndex, frameType, builder);

    // If the larger local is a wide, then its high part is no longer usable. We also need to handle
    // overlapping of the other local and the next local in the iterator.
    if (otherFrameType.isWide()) {
      int lastLocalIndexMarkedTop = otherLocalIndex + 1;
      setSingleLocalToTop(lastLocalIndexMarkedTop, builder);
      handleOverlappingLocals(lastLocalIndexMarkedTop, iterator, otherIterator, builder);
    }
  }

  private void joinLocalsWithSameIndex(
      int localIndex,
      FrameType frameType,
      FrameType otherFrameType,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      CfFrame.Builder builder) {
    if (frameType.isSingle()) {
      if (otherFrameType.isSingle()) {
        joinSingleLocalsWithSameIndex(
            localIndex, frameType.asSingle(), otherFrameType.asSingle(), builder);
      } else {
        setWideLocalToTop(localIndex, builder);
        handleOverlappingLocals(localIndex + 1, iterator, otherIterator, builder);
      }
    } else {
      if (otherFrameType.isWide()) {
        joinWideLocalsWithSameIndex(
            localIndex, frameType.asWide(), otherFrameType.asWide(), builder);
      } else {
        setWideLocalToTop(localIndex, builder);
        handleOverlappingLocals(localIndex + 1, otherIterator, iterator, builder);
      }
    }
  }

  private void joinSingleLocalsWithSameIndex(
      int localIndex,
      SingleFrameType frameType,
      SingleFrameType otherFrameType,
      CfFrame.Builder builder) {
    builder.store(localIndex, frameType.join(otherFrameType));
  }

  private void joinWideLocalsWithSameIndex(
      int localIndex,
      WideFrameType frameType,
      WideFrameType otherFrameType,
      CfFrame.Builder builder) {
    builder.store(localIndex, frameType.join(otherFrameType));
  }

  // TODO(b/231521474): By splitting each wide type into single left/right types, the join of each
  //  (single) local index can be determined by looking at only locals[i] and otherLocals[i] (i.e.,
  //  there is no carry-over). Thus this entire method could be avoided.
  private void handleOverlappingLocals(
      int lastLocalIndexMarkedTop,
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      ObjectBidirectionalIterator<Entry<FrameType>> otherIterator,
      CfFrame.Builder builder) {
    ObjectBidirectionalIterator<Entry<FrameType>> currentIterator = iterator;
    while (currentIterator.hasNext()) {
      Entry<FrameType> entry = nextLocal(currentIterator);
      int currentLocalIndex = entry.getIntKey();
      FrameType currentFrameType = entry.getValue();

      // Check if this local overlaps with the previous wide local that was set to top. If not, then
      // this local is not affected.
      if (lastLocalIndexMarkedTop < currentLocalIndex) {
        // The current local still needs to be handled, thus this rewinds the iterator.
        previousLocal(currentIterator);
        break;
      }

      // Verify that the low part of the current local has been set to top.
      assert builder.hasLocal(currentLocalIndex);
      assert builder.getLocal(currentLocalIndex).isOneWord();

      // If the current local is not a wide, then we're done.
      if (currentFrameType.isSingle()) {
        // The current local has become top due to the overlap with a wide local. Therefore, this
        // intentionally does not rewind the iterator.
        break;
      }

      // The current local is a wide. We mark its high local index as top due to the overlap, and
      // check if this wide local overlaps with a wide local in the other locals.
      lastLocalIndexMarkedTop = currentLocalIndex + 1;
      setSingleLocalToTop(lastLocalIndexMarkedTop, builder);
      currentIterator = currentIterator == iterator ? otherIterator : iterator;
    }
  }

  private void joinLocalsOnlyPresentInOne(
      ObjectBidirectionalIterator<Entry<FrameType>> iterator,
      CfFrame.Builder builder,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    while (iterator.hasNext()) {
      Entry<FrameType> entry = nextLocal(iterator);
      int localIndex = entry.getIntKey();
      FrameType frameType = entry.getValue();
      FrameType joinFrameType = joinWithMissingLocal.apply(frameType);
      assert joinFrameType.isSingle() == frameType.isSingle();
      if (joinFrameType.isOneWord() || joinFrameType.isTwoWord()) {
        setLocalToTop(localIndex, joinFrameType, builder);
      } else {
        builder.store(localIndex, joinFrameType);
      }
    }
  }

  private Entry<FrameType> nextLocal(ObjectBidirectionalIterator<Entry<FrameType>> iterator) {
    Entry<FrameType> entry = iterator.next();
    FrameType frameType = entry.getValue();
    if (frameType.isWide()) {
      assert frameType.isDouble() || frameType.isLong();
      Entry<FrameType> highEntry = iterator.next();
      assert highEntry.getIntKey() == entry.getIntKey() + 1;
      assert highEntry.getValue() == frameType;
    }
    return entry;
  }

  private void previousLocal(ObjectBidirectionalIterator<Entry<FrameType>> iterator) {
    Entry<FrameType> entry = iterator.previous();
    FrameType frameType = entry.getValue();
    if (frameType.isWide()) {
      assert frameType.isDouble() || frameType.isLong();
      Entry<FrameType> lowEntry = iterator.previous();
      assert lowEntry.getIntKey() == entry.getIntKey() - 1;
      assert lowEntry.getValue() == frameType;
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

  private ErroneousCfFrameState joinStack(Deque<PreciseFrameType> stack, CfFrame.Builder builder) {
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
        SingleFrameType join = frameType.asSingle().join(otherFrameType.asSingle());
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
