// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import java.util.function.BiFunction;

/** An analysis state representing that the code does not type check. */
public class ErroneousCfFrameState extends CfFrameState {

  private static final ErroneousCfFrameState INSTANCE = new ErroneousCfFrameState();

  private ErroneousCfFrameState() {}

  static ErroneousCfFrameState getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isError() {
    return true;
  }

  @Override
  public CfFrameState check(AppView<?> appView, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState clear() {
    return this;
  }

  @Override
  public CfFrameState markInitialized(FrameType uninitializedType, DexType initializedType) {
    return this;
  }

  @Override
  public CfFrameState pop() {
    return this;
  }

  @Override
  public CfFrameState pop(BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView,
      DexType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes) {
    return this;
  }

  @Override
  public CfFrameState push(DexType type) {
    return this;
  }

  @Override
  public CfFrameState push(FrameType frameType) {
    return this;
  }

  @Override
  public CfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState storeLocal(int localIndex, FrameType frameType) {
    return this;
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
