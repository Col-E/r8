// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;

@Keep
public class RetraceSourceFileResult {

  private final String filename;
  private final boolean synthesized;

  RetraceSourceFileResult(String filename, boolean synthesized) {
    this.filename = filename;
    this.synthesized = synthesized;
  }

  public boolean isSynthesized() {
    return synthesized;
  }

  public String getFilename() {
    return filename;
  }
}
