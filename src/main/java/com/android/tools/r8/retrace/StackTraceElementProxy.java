// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;

@Keep
public abstract class StackTraceElementProxy<T> {

  public abstract boolean hasClassName();

  public abstract boolean hasMethodName();

  public abstract boolean hasFileName();

  public abstract boolean hasLineNumber();

  public abstract String className();

  public abstract String methodName();

  public abstract String fileName();

  public abstract int lineNumber();
}
