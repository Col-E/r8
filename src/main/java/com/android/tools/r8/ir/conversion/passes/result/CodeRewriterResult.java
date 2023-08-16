// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.result;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier.ControlFlowSimplificationResult;
import com.android.tools.r8.utils.OptionalBool;

public interface CodeRewriterResult {

  CodeRewriterResult NO_CHANGE = new DefaultCodeRewriterResult(false);
  CodeRewriterResult HAS_CHANGED = new DefaultCodeRewriterResult(true);
  CodeRewriterResult NONE = OptionalBool::unknown;

  static CodeRewriterResult hasChanged(boolean hasChanged) {
    return hasChanged ? HAS_CHANGED : NO_CHANGE;
  }

  class DefaultCodeRewriterResult implements CodeRewriterResult {

    private final boolean hasChanged;

    public DefaultCodeRewriterResult(boolean hasChanged) {
      this.hasChanged = hasChanged;
    }

    @Override
    public OptionalBool hasChanged() {
      return OptionalBool.of(hasChanged);
    }
  }

  OptionalBool hasChanged();

  default ControlFlowSimplificationResult asControlFlowSimplificationResult() {
    throw new Unreachable("Not a control flow simplification result.");
  }
}
