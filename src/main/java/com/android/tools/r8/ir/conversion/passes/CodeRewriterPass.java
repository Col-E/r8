// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;

public abstract class CodeRewriterPass<T extends AppInfo> {

  protected final AppView<?> appView;
  protected final DexItemFactory dexItemFactory;
  protected final InternalOptions options;

  protected CodeRewriterPass(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  @SuppressWarnings("unchecked")
  protected AppView<? extends T> appView() {
    return (AppView<? extends T>) appView;
  }

  public final CodeRewriterResult run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Timing timing) {
    return timing.time(getTimingId(), () -> run(code, methodProcessor, methodProcessingContext));
  }

  public final CodeRewriterResult run(IRCode code, Timing timing) {
    return timing.time(getTimingId(), () -> run(code, null, null));
  }

  private CodeRewriterResult run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    if (shouldRewriteCode(code)) {
      assert isAcceptingSSA()
          ? code.isConsistentSSA(appView)
          : code.isConsistentGraph(appView, false);
      CodeRewriterResult result = rewriteCode(code, methodProcessor, methodProcessingContext);
      assert !result.hasChanged()
          || (isProducingSSA()
              ? code.isConsistentSSA(appView)
              : code.isConsistentGraph(appView, false));
      return result;
    }
    return noChange();
  }

  protected CodeRewriterResult noChange() {
    return CodeRewriterResult.NO_CHANGE;
  }

  protected boolean isDebugMode(ProgramMethod context) {
    return options.debug || context.getOrComputeReachabilitySensitive(appView);
  }

  protected abstract String getTimingId();

  protected boolean isAcceptingSSA() {
    return true;
  }

  protected boolean isProducingSSA() {
    return true;
  }

  protected CodeRewriterResult rewriteCode(IRCode code) {
    throw new Unreachable("Should Override or use overload");
  }

  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    return rewriteCode(code);
  }

  protected abstract boolean shouldRewriteCode(IRCode code);
}
