// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatActual;
import static com.android.tools.r8.optimize.interfaces.analysis.ErroneousCfFrameState.formatExpected;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
    return errorUnexpectedStack(frameType, formatExpected(expectedType));
  }

  public static ErroneousCfFrameState errorUnexpectedStack(
      FrameType frameType, FrameType expectedType) {
    return errorUnexpectedStack(frameType, formatExpected(expectedType));
  }

  public static ErroneousCfFrameState errorUnexpectedStack(
      FrameType frameType, ValueType expectedType) {
    return errorUnexpectedStack(frameType, formatExpected(expectedType));
  }

  public static ErroneousCfFrameState errorUnexpectedStack(FrameType frameType, String expected) {
    return internalError(formatActual(frameType), expected, "on stack");
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
  public boolean isGreaterThanOrEquals(AppView<?> appView, CfFrameState state) {
    if (this == state) {
      return true;
    }
    assert appView.hasClassHierarchy();
    CfFrameState leastUpperBound =
        join(appView.withClassHierarchy(), state, UnaryOperator.identity());
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

  @Override
  public boolean isFailedTransferResult() {
    return isError();
  }

  public ErroneousCfFrameState asError() {
    return null;
  }

  public abstract CfFrameState check(CfAnalysisConfig config, CfFrame frame);

  public abstract CfFrameState checkLocals(CfAnalysisConfig config, CfFrame frame);

  public abstract CfFrameState checkStack(CfAnalysisConfig config, CfFrame frame);

  public abstract CfFrameState clear();

  public abstract CfFrameState markInitialized(
      UninitializedFrameType uninitializedType, DexType initializedType);

  public abstract CfFrameState pop();

  public abstract CfFrameState pop(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn);

  public abstract CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, CfAnalysisConfig config);

  public abstract CfFrameState popArray(AppView<?> appView);

  public final CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, DexType expectedType) {
    return popInitialized(appView, config, expectedType, FunctionUtils::getFirst);
  }

  public abstract CfFrameState popInitialized(
      AppView<?> appView,
      CfAnalysisConfig config,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn);

  public abstract CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, DexType... expectedTypes);

  public final CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, MemberType memberType) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return popInitialized(
        appView,
        config,
        FrameType.fromPreciseMemberType(memberType, dexItemFactory)
            .getInitializedType(dexItemFactory));
  }

  public final CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, NumericType expectedType) {
    return popInitialized(appView, config, expectedType.toDexType(appView.dexItemFactory()));
  }

  public final CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, ValueType valueType) {
    return popInitialized(appView, config, valueType, FunctionUtils::getFirst);
  }

  public final CfFrameState popInitialized(
      AppView<?> appView,
      CfAnalysisConfig config,
      ValueType valueType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return popInitialized(appView, config, valueType.toDexType(appView.dexItemFactory()), fn);
  }

  public final CfFrameState popObject(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return pop(
        (state, head) ->
            head.isObject() ? fn.apply(state, head) : errorUnexpectedStack(head, ValueType.OBJECT));
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState popObject(
      AppView<?> appView,
      DexType expectedType,
      CfAnalysisConfig config,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    CfAssignability assignability = config.getAssignability();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return pop(
        (state, head) ->
            head.isObject()
                    && assignability.isAssignable(
                        head.getObjectType(
                            dexItemFactory, config.getCurrentContext().getHolderType()),
                        expectedType)
                ? fn.apply(state, head)
                : errorUnexpectedStack(head, expectedType));
  }

  public final CfFrameState popSingle() {
    return popSingle((state, single) -> state);
  }

  public final CfFrameState popSingle(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return pop(
        (state, single) ->
            single.isSingle()
                ? fn.apply(state, single)
                : errorUnexpectedStack(single, FrameType.oneWord()));
  }

  public final CfFrameState popSingles(
      TriFunction<CfFrameState, PreciseFrameType, PreciseFrameType, CfFrameState> fn) {
    return popSingle(
        (state1, single1) ->
            state1.popSingle((state2, single2) -> fn.apply(state2, single2, single1)));
  }

  public final CfFrameState popSingleOrWide(
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> singleFn,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> wideFn) {
    return pop(
        (state, head) -> head.isSingle() ? singleFn.apply(state, head) : wideFn.apply(state, head));
  }

  public final CfFrameState popSingleSingleOrWide(
      TriFunction<CfFrameState, PreciseFrameType, PreciseFrameType, CfFrameState> singleSingleFn,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> wideFn) {
    return popSingleOrWide(
        (state1, single1) ->
            state1.popSingle((state2, single2) -> singleSingleFn.apply(state2, single2, single1)),
        wideFn);
  }

  public abstract CfFrameState push(CfAnalysisConfig config, DexType type);

  public abstract CfFrameState push(CfAnalysisConfig config, TypeElement type);

  public abstract CfFrameState push(CfAnalysisConfig config, PreciseFrameType frameType);

  public final CfFrameState push(
      CfAnalysisConfig config, PreciseFrameType frameType, PreciseFrameType frameType2) {
    return push(config, frameType).push(config, frameType2);
  }

  public final CfFrameState push(
      CfAnalysisConfig config,
      PreciseFrameType frameType,
      PreciseFrameType frameType2,
      PreciseFrameType frameType3) {
    return push(config, frameType).push(config, frameType2).push(config, frameType3);
  }

  public final CfFrameState push(
      CfAnalysisConfig config,
      PreciseFrameType frameType,
      PreciseFrameType frameType2,
      PreciseFrameType frameType3,
      PreciseFrameType frameType4) {
    return push(config, frameType)
        .push(config, frameType2)
        .push(config, frameType3)
        .push(config, frameType4);
  }

  public final CfFrameState push(
      CfAnalysisConfig config,
      PreciseFrameType frameType,
      PreciseFrameType frameType2,
      PreciseFrameType frameType3,
      PreciseFrameType frameType4,
      PreciseFrameType frameType5) {
    return push(config, frameType)
        .push(config, frameType2)
        .push(config, frameType3)
        .push(config, frameType4)
        .push(config, frameType5);
  }

  public final CfFrameState push(
      CfAnalysisConfig config,
      PreciseFrameType frameType,
      PreciseFrameType frameType2,
      PreciseFrameType frameType3,
      PreciseFrameType frameType4,
      PreciseFrameType frameType5,
      PreciseFrameType frameType6) {
    return push(config, frameType)
        .push(config, frameType2)
        .push(config, frameType3)
        .push(config, frameType4)
        .push(config, frameType5)
        .push(config, frameType6);
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(
      AppView<?> appView, CfAnalysisConfig config, MemberType memberType) {
    return push(config, FrameType.fromPreciseMemberType(memberType, appView.dexItemFactory()));
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(
      AppView<?> appView, CfAnalysisConfig config, NumericType numericType) {
    return push(config, numericType.toDexType(appView.dexItemFactory()));
  }

  @SuppressWarnings("InconsistentOverloads")
  public final CfFrameState push(AppView<?> appView, CfAnalysisConfig config, ValueType valueType) {
    return push(config, valueType.toDexType(appView.dexItemFactory()));
  }

  public abstract CfFrameState pushException(CfAnalysisConfig config, DexType guard);

  public abstract CfFrameState readLocal(
      AppView<?> appView,
      CfAnalysisConfig config,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> consumer);

  public abstract CfFrameState storeLocal(
      int localIndex, FrameType frameType, CfAnalysisConfig config);

  public final CfFrameState storeLocal(
      int localIndex,
      PrimitiveTypeElement primitiveType,
      AppView<?> appView,
      CfAnalysisConfig config) {
    assert primitiveType.isInt()
        || primitiveType.isFloat()
        || primitiveType.isLong()
        || primitiveType.isDouble();
    return storeLocal(
        localIndex,
        FrameType.initialized(primitiveType.toDexType(appView.dexItemFactory())),
        config);
  }

  @Override
  public final CfFrameState join(AppView<?> appView, CfFrameState state) {
    assert appView.hasClassHierarchy();
    return join(
        appView.withClassHierarchy(),
        state,
        frameType -> frameType.isSingle() ? FrameType.oneWord() : FrameType.twoWord());
  }

  public final CfFrameState join(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CfFrameState state,
      UnaryOperator<FrameType> joinWithMissingLocal) {
    if (state.isBottom() || isError()) {
      return this;
    }
    if (isBottom() || state.isError()) {
      return state;
    }
    assert isConcrete();
    assert state.isConcrete();
    return asConcrete().join(appView, state.asConcrete(), joinWithMissingLocal);
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();
}
