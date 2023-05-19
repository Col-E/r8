// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;

@Keep
public interface RetraceStackTraceElementProxy<T, ST extends StackTraceElementProxy<T, ST>>
    extends Comparable<RetraceStackTraceElementProxy<T, ST>> {

  boolean isAmbiguous();

  boolean isTopFrame();

  boolean hasRetracedClass();

  boolean hasRetracedMethod();

  boolean hasRetracedField();

  boolean hasSourceFile();

  boolean hasLineNumber();

  boolean hasRetracedFieldOrReturnType();

  boolean hasRetracedMethodArguments();

  ST getOriginalItem();

  RetracedClassReference getRetracedClass();

  RetracedMethodReference getRetracedMethod();

  RetracedFieldReference getRetracedField();

  RetracedTypeReference getRetracedFieldOrReturnType();

  List<RetracedTypeReference> getRetracedMethodArguments();

  String getSourceFile();

  RetracedSourceFile getRetracedSourceFile();

  int getLineNumber();

  RetraceStackTraceContext getContext();
}
