// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.internal.RetraceFrameResultImpl.ElementImpl;

public class RetracedSingleFrameImpl implements RetracedSingleFrame {

  private final ElementImpl frameElement;
  private final RetracedMethodReference methodReference;
  private final int index;

  private RetracedSingleFrameImpl(
      ElementImpl frameElement, RetracedMethodReference methodReference, int index) {
    this.frameElement = frameElement;
    this.methodReference = methodReference;
    this.index = index;
  }

  @Override
  public RetracedMethodReference getMethodReference() {
    return methodReference;
  }

  @Override
  public int getIndex() {
    return index;
  }

  @Override
  public RetracedSourceFile getSourceFile() {
    return frameElement.getSourceFile(getMethodReference());
  }

  static RetracedSingleFrameImpl create(
      ElementImpl frameElement, RetracedMethodReference methodReference, int index) {
    return new RetracedSingleFrameImpl(frameElement, methodReference, index);
  }
}
