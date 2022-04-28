// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class CfFrameState extends AbstractState<CfFrameState> {

  public static CfFrameState bottom() {
    return BottomCfFrameState.getInstance();
  }

  public static ErroneousCfFrameState error() {
    return ErroneousCfFrameState.getInstance();
  }

  @Override
  public CfFrameState asAbstractState() {
    return this;
  }

  public abstract CfFrameState markInitialized(
      FrameType uninitializedType, DexType initializedType);

  public abstract CfFrameState pop();

  public abstract CfFrameState pop(Function<FrameType, CfFrameState> fn);

  public abstract CfFrameState pop(AppView<?> appView, FrameType expectedType);

  public abstract CfFrameState pop(
      AppView<?> appView,
      FrameType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn);

  public abstract CfFrameState pop(AppView<?> appView, FrameType... expectedTypes);

  public abstract CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context);

  public abstract CfFrameState popInitialized(AppView<?> appView, DexType expectedType);

  public abstract CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes);

  public final CfFrameState popInitialized(AppView<?> appView, MemberType memberType) {
    return pop(appView, FrameType.fromMemberType(memberType, appView.dexItemFactory()));
  }

  public final CfFrameState popInitialized(AppView<?> appView, NumericType expectedType) {
    return popInitialized(appView, expectedType.toDexType(appView.dexItemFactory()));
  }

  // TODO(b/214496607): Pushing a value should return an error if the stack grows larger than the
  //  max stack height.
  public abstract CfFrameState push(DexType type);

  // TODO(b/214496607): Pushing a value should return an error if the stack grows larger than the
  //  max stack height.
  public abstract CfFrameState push(FrameType frameType);

  public final CfFrameState push(AppView<?> appView, MemberType memberType) {
    return push(FrameType.fromMemberType(memberType, appView.dexItemFactory()));
  }

  public final CfFrameState push(AppView<?> appView, NumericType numericType) {
    return push(numericType.toDexType(appView.dexItemFactory()));
  }

  public final CfFrameState push(AppView<?> appView, ValueType valueType) {
    return push(valueType.toDexType(appView.dexItemFactory()));
  }

  @Override
  public final CfFrameState join(CfFrameState state) {
    // TODO(b/214496607): Implement join.
    throw new Unimplemented();
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();
}
