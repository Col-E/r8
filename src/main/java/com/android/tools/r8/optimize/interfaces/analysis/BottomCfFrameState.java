// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
  public CfFrameState check(CfAnalysisConfig config, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkLocals(CfAnalysisConfig config, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkStack(CfAnalysisConfig config, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState clear() {
    return this;
  }

  @Override
  public CfFrameState markInitialized(
      UninitializedFrameType uninitializedType, DexType initializedType) {
    return this;
  }

  @Override
  public CfFrameState pop() {
    return this;
  }

  @Override
  public CfFrameState pop(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, CfAnalysisConfig config) {
    return this;
  }

  @Override
  public CfFrameState popArray(AppView<?> appView) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView,
      CfAnalysisConfig config,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, DexType... expectedTypes) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, TypeElement type) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, PreciseFrameType frameType) {
    return this;
  }

  @Override
  public CfFrameState pushException(CfAnalysisConfig config, DexType guard) {
    return this;
  }

  @Override
  public CfFrameState readLocal(
      AppView<?> appView,
      CfAnalysisConfig config,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState storeLocal(int localIndex, FrameType frameType, CfAnalysisConfig config) {
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
