// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetraceSourceFileResult;

public class RetraceSourceFileResultImpl implements RetraceSourceFileResult {

  private final String filename;
  private final boolean synthesized;

  RetraceSourceFileResultImpl(String filename, boolean synthesized) {
    this.filename = filename;
    this.synthesized = synthesized;
  }

  @Override
  public boolean isSynthesized() {
    return synthesized;
  }

  @Override
  public String getFilename() {
    return filename;
  }
}
