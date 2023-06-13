// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
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
  AppView<? extends T> appView() {
    return (AppView<? extends T>) appView;
  }

  public final void run(ProgramMethod method, IRCode code, Timing timing) {
    timing.time(getTimingId(), () -> run(method, code));
  }

  public final void run(ProgramMethod method, IRCode code) {
    if (shouldRewriteCode(method, code)) {
      rewriteCode(method, code);
    }
  }

  protected abstract String getTimingId();

  protected abstract void rewriteCode(ProgramMethod method, IRCode code);

  protected abstract boolean shouldRewriteCode(ProgramMethod method, IRCode code);
}
