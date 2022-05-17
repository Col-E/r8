// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.function.BiFunction;

public class BottomCfFrameState extends CfFrameState {

  private static final BottomCfFrameState INSTANCE = new BottomCfFrameState();

  private BottomCfFrameState() {}

  static BottomCfFrameState getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public CfFrameState check(AppView<?> appView, CfFrame frame) {
    return new ConcreteCfFrameState().check(appView, frame);
  }

  @Override
  public CfFrameState clear() {
    return this;
  }

  @Override
  public CfFrameState markInitialized(FrameType uninitializedType, DexType initializedType) {
    // Initializing an uninitialized type is a no-op when the frame is empty.
    return this;
  }

  @Override
  public ErroneousCfFrameState pop() {
    return error("Unexpected pop from empty stack");
  }

  @Override
  public ErroneousCfFrameState pop(BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop();
  }

  @Override
  public ErroneousCfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, CfAnalysisConfig config) {
    return pop();
  }

  @Override
  public ErroneousCfFrameState popInitialized(
      AppView<?> appView,
      DexType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return pop();
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes) {
    return expectedTypes.length == 0 ? this : pop();
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
    return new ConcreteCfFrameState().push(config, type);
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, FrameType frameType) {
    return new ConcreteCfFrameState().push(config, frameType);
  }

  @Override
  public ErroneousCfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return error("Unexpected local read from empty frame");
  }

  @Override
  public CfFrameState storeLocal(int localIndex, FrameType frameType, CfAnalysisConfig config) {
    return new ConcreteCfFrameState().storeLocal(localIndex, frameType, config);
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
