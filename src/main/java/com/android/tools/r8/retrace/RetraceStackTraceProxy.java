// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;

@Keep
public interface RetraceStackTraceProxy<T extends StackTraceElementProxy<?>> {

  boolean isAmbiguous();

  boolean hasRetracedClass();

  boolean hasRetracedMethod();

  boolean hasSourceFile();

  boolean hasLineNumber();

  T getOriginalItem();

  RetracedClass getRetracedClass();

  RetracedMethod getRetracedMethod();

  String getSourceFile();

  int getLineNumber();
}
