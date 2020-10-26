// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;

@Keep
public interface RetraceStackTraceProxy<T extends StackTraceElementProxy<?>>
    extends Comparable<RetraceStackTraceProxy<T>> {

  boolean isAmbiguous();

  boolean isTopFrame();

  boolean hasRetracedClass();

  boolean hasRetracedMethod();

  boolean hasRetracedField();

  boolean hasSourceFile();

  boolean hasLineNumber();

  boolean hasFieldOrReturnType();

  boolean hasMethodArguments();

  T getOriginalItem();

  RetracedClass getRetracedClass();

  RetracedMethod getRetracedMethod();

  RetracedField getRetracedField();

  RetracedType getRetracedFieldOrReturnType();

  List<RetracedType> getMethodArguments();

  String getSourceFile();

  int getLineNumber();
}
