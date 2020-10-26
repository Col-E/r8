// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;

@Keep
public abstract class StackTraceElementProxy<T> {

  public abstract boolean hasClassName();

  public abstract boolean hasMethodName();

  public abstract boolean hasFileName();

  public abstract boolean hasLineNumber();

  public abstract boolean hasFieldName();

  public abstract boolean hasFieldOrReturnType();

  public abstract boolean hasMethodArguments();

  public abstract ClassReference getClassReference();

  public abstract String getMethodName();

  public abstract String getFileName();

  public abstract int getLineNumber();

  public abstract String getFieldName();

  public abstract String getFieldOrReturnType();

  public abstract String getMethodArguments();
}
