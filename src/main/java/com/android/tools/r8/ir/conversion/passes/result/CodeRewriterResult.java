// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.result;

import com.android.tools.r8.errors.Unreachable;

public interface CodeRewriterResult {

  CodeRewriterResult NO_CHANGE = new DefaultCodeRewriterResult(false);
  CodeRewriterResult HAS_CHANGED = new DefaultCodeRewriterResult(true);
  CodeRewriterResult NONE =
      new CodeRewriterResult() {
        @Override
        public boolean hasChanged() {
          throw new Unreachable();
        }
      };

  static CodeRewriterResult hasChanged(boolean hasChanged) {
    return hasChanged ? HAS_CHANGED : NO_CHANGE;
  }

  class DefaultCodeRewriterResult implements CodeRewriterResult {

    private final boolean hasChanged;

    public DefaultCodeRewriterResult(boolean hasChanged) {
      this.hasChanged = hasChanged;
    }

    @Override
    public boolean hasChanged() {
      return hasChanged;
    }
  }

  boolean hasChanged();
}
