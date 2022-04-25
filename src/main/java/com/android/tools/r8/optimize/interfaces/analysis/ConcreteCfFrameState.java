// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.cf.code.CfFrame.getInitializedFrameType;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Function;

public class ConcreteCfFrameState extends CfFrameState {

  private final Int2ObjectSortedMap<FrameType> locals;
  private final Deque<FrameType> stack;

  ConcreteCfFrameState() {
    this(new Int2ObjectAVLTreeMap<>(), new ArrayDeque<>());
  }

  ConcreteCfFrameState(Int2ObjectSortedMap<FrameType> locals, Deque<FrameType> stack) {
    this.locals = locals;
    this.stack = stack;
  }

  @Override
  public CfFrameState markInitialized(FrameType uninitializedType, DexType initializedType) {
    if (uninitializedType.isInitialized()) {
      return error();
    }
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      FrameType frameType = entry.getValue();
      FrameType initializedFrameType =
          getInitializedFrameType(uninitializedType, frameType, initializedType);
      entry.setValue(initializedFrameType);
    }
    // TODO(b/214496607): By using a collection that supports element replacement this could mutate
    //  the existing stack instead of building a new one.
    Deque<FrameType> newStack = new ArrayDeque<>();
    for (FrameType frameType : stack) {
      FrameType initializedFrameType =
          getInitializedFrameType(uninitializedType, frameType, initializedType);
      newStack.addLast(initializedFrameType);
    }
    return new ConcreteCfFrameState(locals, newStack);
  }

  @Override
  public CfFrameState pop() {
    if (stack.isEmpty()) {
      return error();
    }
    stack.removeLast();
    return this;
  }

  @Override
  public CfFrameState pop(Function<FrameType, CfFrameState> fn) {
    if (stack.isEmpty()) {
      return error();
    }
    FrameType frameType = stack.removeLast();
    return fn.apply(frameType);
  }

  @Override
  public CfFrameState pop(AppView<?> appView, FrameType expectedType) {
    return pop(appView, expectedType, ignore -> this);
  }

  @Override
  public CfFrameState pop(
      AppView<?> appView, FrameType expectedType, Function<FrameType, CfFrameState> fn) {
    return pop(
        frameType ->
            CfAssignability.isAssignable(frameType, expectedType, appView)
                ? fn.apply(frameType)
                : error());
  }

  @Override
  public CfFrameState pop(AppView<?> appView, FrameType... expectedTypes) {
    CfFrameState state = this;
    for (int i = expectedTypes.length - 1; i >= 0; i--) {
      state = state.pop(appView, expectedTypes[i]);
    }
    return state;
  }

  @Override
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context) {
    return pop(
        frameType -> {
          if (frameType.isUninitializedThis()) {
            if (constructor.getHolderType() == context.getHolderType()
                || constructor.getHolderType() == context.getHolder().getSuperType()) {
              return markInitialized(frameType, context.getHolderType());
            }
          } else if (frameType.isUninitializedNew()) {
            DexType uninitializedNewType = frameType.getUninitializedNewType();
            if (constructor.getHolderType() == uninitializedNewType) {
              return markInitialized(frameType, uninitializedNewType);
            }
          }
          return error();
        });
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType expectedType) {
    return pop(
        frameType ->
            frameType.isInitialized()
                    && CfAssignability.isAssignable(
                        frameType.getInitializedType(), expectedType, appView)
                ? this
                : error());
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
  public CfFrameState push(DexType type) {
    return push(FrameType.initialized(type));
  }

  @Override
  public CfFrameState push(FrameType frameType) {
    stack.push(frameType);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // TODO(b/214496607): FrameType should implement equals() and hashCode().
    ConcreteCfFrameState that = (ConcreteCfFrameState) o;
    return locals.equals(that.locals) && stack.equals(that.stack);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locals, stack);
  }
}
