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
      AppView<?> appView, FrameType expectedType, Function<FrameType, CfFrameState> fn);

  public abstract CfFrameState pop(AppView<?> appView, FrameType... expectedTypes);

  public abstract CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, ProgramMethod context);

  public abstract CfFrameState popInitialized(AppView<?> appView, DexType expectedType);

  public abstract CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes);

  public abstract CfFrameState push(DexType type);

  public abstract CfFrameState push(FrameType frameType);

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
