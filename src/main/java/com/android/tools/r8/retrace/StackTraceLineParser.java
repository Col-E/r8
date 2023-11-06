// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser;

@KeepForApi
public interface StackTraceLineParser<T, ST extends StackTraceElementProxy<T, ST>> {

  ST parse(T stackTraceLine);

  static StackTraceLineParser<String, StackTraceElementStringProxy> createRegularExpressionParser(
      String regularExpression) {
    return new StackTraceRegularExpressionParser(regularExpression);
  }
}
