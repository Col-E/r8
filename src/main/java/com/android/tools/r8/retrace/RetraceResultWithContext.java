// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;

@Keep
public interface RetraceResultWithContext {

  /**
   * The current context after retracing stack trace lines.
   *
   * <p>Use this context as the next context when retracing additional frames.
   *
   * @return The stack trace context.
   */
  RetraceStackTraceContext getContext();
}
