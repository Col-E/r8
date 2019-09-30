// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode.SourceCodeProvider;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.util.List;

// TODO(b/134732760): I think a general clean-up is required here.
// Instead of being a sourceCodeProvider, this should just have API getCfCode() and not
// get(Position). All users should be rewritten to use getCfCode(). That would simplify a lot the
// code everywhere.
public abstract class CfSyntheticSourceCodeProvider implements SourceCodeProvider {

  private final DexEncodedMethod method;
  private final DexMethod originalMethod;
  protected final AppView<?> appView;

  public CfSyntheticSourceCodeProvider(
      DexEncodedMethod method, DexMethod originalMethod, AppView<?> appView) {
    this.method = method;
    this.originalMethod = originalMethod;
    this.appView = appView;
  }

  @Override
  public SourceCode get(Position callerPosition) {
    CfCode code = generateCfCode(callerPosition);
    return new CfSourceCode(
        code,
        code.getLocalVariables(),
        method,
        originalMethod,
        callerPosition,
        Origin.unknown(),
        appView);
  }

  public CfCode getCfCode() {
    return generateCfCode(null);
  }

  protected abstract CfCode generateCfCode(Position callerPosition);

  protected CfCode standardCfCodeFromInstructions(List<CfInstruction> instructions) {
    return new CfCode(
        method.method.holder,
        defaultMaxStack(),
        defaultMaxLocals(),
        instructions,
        defaultTryCatchs(),
        ImmutableList.of());
  }

  protected int defaultMaxStack() {
    return 16;
  }

  protected int defaultMaxLocals() {
    return 16;
  }

  protected List<CfTryCatch> defaultTryCatchs() {
    return ImmutableList.of();
  }
}
