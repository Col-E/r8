// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public interface RetraceFrameElement extends RetraceElement<RetraceFrameResult> {

  boolean isUnknown();

  RetracedMethodReference getTopFrame();

  RetraceClassElement getClassElement();

  void visitFrames(Consumer<RetracedSingleFrame> consumer);

  Stream<RetracedSingleFrame> forEachFrame();

  void visitRewrittenFrames(
      RetraceStackTraceContext context, Consumer<RetracedSingleFrame> consumer);

  Stream<RetracedSingleFrame> forEachRewrittenFrame(RetraceStackTraceContext context);

  RetracedSourceFile getSourceFile(RetracedClassMemberReference frame);

  List<? extends RetracedMethodReference> getOuterFrames();

  RetraceStackTraceContext getContext();
}
