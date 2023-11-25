// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import java.util.Optional;

public class CfFrameVerifierDefaultAnalysisConfig implements CfAnalysisConfig {

  private final CfAssignability assignability;
  private final CfCode code;
  private final ProgramMethod method;
  private final Optional<DexMethod> previousMethod;

  CfFrameVerifierDefaultAnalysisConfig(
      AppView<?> appView, CfCode code, ProgramMethod method, Optional<DexMethod> previousMethod) {
    this.assignability = new CfAssignability(appView);
    this.code = code;
    this.method = method;
    this.previousMethod = previousMethod;
  }

  @Override
  public CfAssignability getAssignability() {
    return assignability;
  }

  @Override
  public DexMethod getCurrentContext() {
    return previousMethod.orElse(method.getReference());
  }

  @Override
  public int getMaxLocals() {
    return code.getMaxLocals();
  }

  @Override
  public int getMaxStack() {
    return code.getMaxStack();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
    // If the code is rewritten according to the graph lens, we perform a strict check that the
    // given type is the same as the current holder's super class.
    if (!previousMethod.isPresent()) {
      return type == method.getHolder().getSuperType();
    }
    // Otherwise, we don't know what the super class of the current class was at the point of the
    // code lens. We return true, which has the consequence that we may accept a constructor call
    // for an uninitialized-this value where the constructor is not defined in the immediate parent
    // class.
    return true;
  }

  @Override
  public boolean isStrengthenFramesEnabled() {
    return false;
  }
}
