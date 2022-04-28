// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BottomCfFrameState extends CfFrameState {

  private static final BottomCfFrameState INSTANCE = new BottomCfFrameState();

  private BottomCfFrameState() {}

  static BottomCfFrameState getInstance() {
    return INSTANCE;
  }

  @Override
  public CfFrameState markInitialized(FrameType uninitializedType, DexType initializedType) {
    return error();
  }

  @Override
  public CfFrameState pop() {
    return error();
  }

  @Override
  public CfFrameState pop(Function<FrameType, CfFrameState> fn) {
    return error();
  }

  @Override
  public CfFrameState pop(AppView<?> appView, FrameType expectedType) {
    return error();
  }

  @Override
  public CfFrameState pop(
      AppView<?> appView,
      FrameType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return error();
  }

  @Override
  public CfFrameState pop(AppView<?> appView, FrameType... expectedTypes) {
    return error();
  }

  @Override
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context) {
    return error();
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType expectedType) {
    return error();
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes) {
    return error();
  }

  @Override
  public CfFrameState push(DexType type) {
    return new ConcreteCfFrameState().push(type);
  }

  @Override
  public CfFrameState push(FrameType frameType) {
    return new ConcreteCfFrameState().push(frameType);
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
