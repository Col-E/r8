// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.FunctionUtils;
import com.android.tools.r8.utils.TriFunction;
import java.util.function.BiFunction;

public abstract class CfFrameState extends AbstractState<CfFrameState> {

  public static BottomCfFrameState bottom() {
    return BottomCfFrameState.getInstance();
  }

  public static ErroneousCfFrameState error() {
    return ErroneousCfFrameState.getInstance();
  }

  @Override
  public CfFrameState asAbstractState() {
    return this;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isConcrete() {
    return false;
  }

  public ConcreteCfFrameState asConcrete() {
    return null;
  }

  public boolean isError() {
    return false;
  }

  public abstract CfFrameState check(AppView<?> appView, CfFrame frame);

  public abstract CfFrameState clear();

  public abstract CfFrameState markInitialized(
      FrameType uninitializedType, DexType initializedType);

  public abstract CfFrameState pop();

  public abstract CfFrameState pop(BiFunction<CfFrameState, FrameType, CfFrameState> fn);

  public abstract CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context);

  public final CfFrameState popInitialized(AppView<?> appView, DexType expectedType) {
    return popInitialized(appView, expectedType, FunctionUtils::getFirst);
  }

  public abstract CfFrameState popInitialized(
      AppView<?> appView,
      DexType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn);

  public abstract CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes);

  public final CfFrameState popInitialized(AppView<?> appView, MemberType memberType) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return popInitialized(
        appView,
        FrameType.fromPreciseMemberType(memberType, dexItemFactory)
            .getInitializedType(dexItemFactory));
  }

  public final CfFrameState popInitialized(AppView<?> appView, NumericType expectedType) {
    return popInitialized(appView, expectedType.toDexType(appView.dexItemFactory()));
  }

  public final CfFrameState popInitialized(AppView<?> appView, ValueType valueType) {
    return popInitialized(appView, valueType, FunctionUtils::getFirst);
  }

  public final CfFrameState popInitialized(
      AppView<?> appView,
      ValueType valueType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return popInitialized(appView, valueType.toDexType(appView.dexItemFactory()), fn);
  }

  public final CfFrameState popObject(BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop((state, head) -> head.isObject() ? fn.apply(state, head) : error());
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState popObject(
      AppView<?> appView,
      DexType expectedType,
      ProgramMethod context,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop(
        (state, head) ->
            head.isObject()
                    && CfAssignability.isAssignable(
                        head.getObjectType(context), expectedType, appView)
                ? fn.apply(state, head)
                : error());
  }

  public final CfFrameState popSingle() {
    return popSingle((state, single) -> state);
  }

  public final CfFrameState popSingle(BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop((state, single) -> single.isSingle() ? fn.apply(state, single) : error());
  }

  public final CfFrameState popSingles(
      TriFunction<CfFrameState, FrameType, FrameType, CfFrameState> fn) {
    return popSingle(
        (state1, single1) ->
            state1.popSingle((state2, single2) -> fn.apply(state2, single2, single1)));
  }

  public final CfFrameState popSingleOrWide(
      BiFunction<CfFrameState, FrameType, CfFrameState> singleFn,
      BiFunction<CfFrameState, FrameType, CfFrameState> wideFn) {
    return pop(
        (state, head) -> head.isSingle() ? singleFn.apply(state, head) : wideFn.apply(state, head));
  }

  public final CfFrameState popSingleSingleOrWide(
      TriFunction<CfFrameState, FrameType, FrameType, CfFrameState> singleSingleFn,
      BiFunction<CfFrameState, FrameType, CfFrameState> wideFn) {
    return popSingleOrWide(
        (state1, single1) ->
            state1.popSingle((state2, single2) -> singleSingleFn.apply(state2, single2, single1)),
        wideFn);
  }

  // TODO(b/214496607): Pushing a value should return an error if the stack grows larger than the
  //  max stack height.
  public abstract CfFrameState push(DexType type);

  // TODO(b/214496607): Pushing a value should return an error if the stack grows larger than the
  //  max stack height.
  public abstract CfFrameState push(FrameType frameType);

  public final CfFrameState push(FrameType frameType, FrameType frameType2) {
    return push(frameType).push(frameType2);
  }

  public final CfFrameState push(FrameType frameType, FrameType frameType2, FrameType frameType3) {
    return push(frameType).push(frameType2).push(frameType3);
  }

  public final CfFrameState push(
      FrameType frameType, FrameType frameType2, FrameType frameType3, FrameType frameType4) {
    return push(frameType).push(frameType2).push(frameType3).push(frameType4);
  }

  public final CfFrameState push(
      FrameType frameType,
      FrameType frameType2,
      FrameType frameType3,
      FrameType frameType4,
      FrameType frameType5) {
    return push(frameType).push(frameType2).push(frameType3).push(frameType4).push(frameType5);
  }

  public final CfFrameState push(
      FrameType frameType,
      FrameType frameType2,
      FrameType frameType3,
      FrameType frameType4,
      FrameType frameType5,
      FrameType frameType6) {
    return push(frameType)
        .push(frameType2)
        .push(frameType3)
        .push(frameType4)
        .push(frameType5)
        .push(frameType6);
  }

  public final CfFrameState push(AppView<?> appView, MemberType memberType) {
    return push(FrameType.fromPreciseMemberType(memberType, appView.dexItemFactory()));
  }

  public final CfFrameState push(AppView<?> appView, NumericType numericType) {
    return push(numericType.toDexType(appView.dexItemFactory()));
  }

  public final CfFrameState push(AppView<?> appView, ValueType valueType) {
    return push(valueType.toDexType(appView.dexItemFactory()));
  }

  public abstract CfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> consumer);

  public abstract CfFrameState storeLocal(int localIndex, FrameType frameType);

  public final CfFrameState storeLocal(
      int localIndex, PrimitiveTypeElement primitiveType, AppView<?> appView) {
    assert primitiveType.isInt()
        || primitiveType.isFloat()
        || primitiveType.isLong()
        || primitiveType.isDouble();
    return storeLocal(
        localIndex, FrameType.initialized(primitiveType.toDexType(appView.dexItemFactory())));
  }

  @Override
  public final CfFrameState join(CfFrameState state) {
    if (state.isBottom() || isError()) {
      return this;
    }
    if (isBottom() || state.isError()) {
      return state;
    }
    assert isConcrete();
    assert state.isConcrete();
    return asConcrete().join(state.asConcrete());
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();
}
