// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;

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
