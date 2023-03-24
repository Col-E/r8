// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;

@Keep
public class RetracePartitionException extends RuntimeException {

  public RetracePartitionException(String message) {
    super(message);
  }

  public RetracePartitionException(Exception e) {
    super(e);
  }
}
