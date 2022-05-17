// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatActual;
import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatExpected;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
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
import java.util.function.UnaryOperator;

public abstract class CfFrameState extends AbstractState<CfFrameState> {

  public static BottomCfFrameState bottom() {
    return BottomCfFrameState.getInstance();
  }

  public static ErroneousCfFrameState error(String message) {
    return new ErroneousCfFrameState(message);
  }

  public static ErroneousCfFrameState errorUnexpectedLocal(
      FrameType frameType, ValueType expectedType, int localIndex) {
    return internalError(
        formatActual(frameType), formatExpected(expectedType), "at local index " + localIndex);
  }

  public static ErroneousCfFrameState errorUnexpectedStack(
      FrameType frameType, DexType expectedType) {
    return internalErrorUnexpectedStack(formatActual(frameType), formatExpected(expectedType));
  }

  public static ErroneousCfFrameState errorUnexpectedStack(
      FrameType frameType, FrameType expectedType) {
    return internalErrorUnexpectedStack(formatActual(frameType), formatExpected(expectedType));
  }

  public static ErroneousCfFrameState errorUnexpectedStack(
      FrameType frameType, ValueType expectedType) {
    return internalErrorUnexpectedStack(formatActual(frameType), formatExpected(expectedType));
  }

  private static ErroneousCfFrameState internalErrorUnexpectedStack(
      String actual, String expected) {
    return internalError(actual, expected, "on stack");
  }

  private static ErroneousCfFrameState internalError(
      String actual, String expected, String location) {
    return error("Expected " + expected + " " + location + ", but was " + actual);
  }

  @Override
  public CfFrameState asAbstractState() {
    return this;
  }

  @Override
  public boolean isGreaterThanOrEquals(CfFrameState state) {
    if (this == state) {
      return true;
    }
    CfFrameState leastUpperBound = join(state, UnaryOperator.identity());
    return equals(leastUpperBound);
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

  public ErroneousCfFrameState asError() {
    return null;
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
    return pop(
        (state, head) ->
            head.isObject() ? fn.apply(state, head) : errorUnexpectedStack(head, ValueType.OBJECT));
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
                : errorUnexpectedStack(head, expectedType));
  }

  public final CfFrameState popSingle() {
    return popSingle((state, single) -> state);
  }

  public final CfFrameState popSingle(BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop(
        (state, single) ->
            single.isSingle()
                ? fn.apply(state, single)
                : errorUnexpectedStack(single, FrameType.oneWord()));
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

  public abstract CfFrameState push(CfCode code, DexType type);

  public abstract CfFrameState push(CfCode code, FrameType frameType);

  public final CfFrameState push(CfCode code, FrameType frameType, FrameType frameType2) {
    return push(code, frameType).push(code, frameType2);
  }

  public final CfFrameState push(
      CfCode code, FrameType frameType, FrameType frameType2, FrameType frameType3) {
    return push(code, frameType).push(code, frameType2).push(code, frameType3);
  }

  public final CfFrameState push(
      CfCode code,
      FrameType frameType,
      FrameType frameType2,
      FrameType frameType3,
      FrameType frameType4) {
    return push(code, frameType)
        .push(code, frameType2)
        .push(code, frameType3)
        .push(code, frameType4);
  }

  public final CfFrameState push(
      CfCode code,
      FrameType frameType,
      FrameType frameType2,
      FrameType frameType3,
      FrameType frameType4,
      FrameType frameType5) {
    return push(code, frameType)
        .push(code, frameType2)
        .push(code, frameType3)
        .push(code, frameType4)
        .push(code, frameType5);
  }

  public final CfFrameState push(
      CfCode code,
      FrameType frameType,
      FrameType frameType2,
      FrameType frameType3,
      FrameType frameType4,
      FrameType frameType5,
      FrameType frameType6) {
    return push(code, frameType)
        .push(code, frameType2)
        .push(code, frameType3)
        .push(code, frameType4)
        .push(code, frameType5)
        .push(code, frameType6);
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(AppView<?> appView, CfCode code, MemberType memberType) {
    return push(code, FrameType.fromPreciseMemberType(memberType, appView.dexItemFactory()));
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(AppView<?> appView, CfCode code, NumericType numericType) {
    return push(code, numericType.toDexType(appView.dexItemFactory()));
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(AppView<?> appView, CfCode code, ValueType valueType) {
    return push(code, valueType.toDexType(appView.dexItemFactory()));
  }

  public abstract CfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> consumer);

  public abstract CfFrameState storeLocal(int localIndex, FrameType frameType, CfCode code);

  public final CfFrameState storeLocal(
      int localIndex, PrimitiveTypeElement primitiveType, AppView<?> appView, CfCode code) {
    assert primitiveType.isInt()
        || primitiveType.isFloat()
        || primitiveType.isLong()
        || primitiveType.isDouble();
    return storeLocal(
        localIndex, FrameType.initialized(primitiveType.toDexType(appView.dexItemFactory())), code);
  }

  @Override
  public final CfFrameState join(CfFrameState state) {
    return join(
        state, frameType -> frameType.isSingle() ? FrameType.oneWord() : FrameType.twoWord());
  }

  public final CfFrameState join(
      CfFrameState state, UnaryOperator<FrameType> joinWithMissingLocal) {
    if (state.isBottom() || isError()) {
      return this;
    }
    if (isBottom() || state.isError()) {
      return state;
    }
    assert isConcrete();
    assert state.isConcrete();
    return asConcrete().join(state.asConcrete(), joinWithMissingLocal);
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();
}
