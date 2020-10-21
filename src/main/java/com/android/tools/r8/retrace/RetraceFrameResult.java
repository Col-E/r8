// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public interface RetraceFrameResult {

  Stream<Element> stream();

  RetraceFrameResult forEach(Consumer<Element> resultConsumer);

  boolean isAmbiguous();

  @Keep
  interface Element {

    boolean isUnknown();

    RetracedMethod getTopFrame();

    RetraceClassResult.Element getClassElement();

    void visitFrames(BiConsumer<RetracedMethod, Integer> consumer);

    RetraceSourceFileResult retraceSourceFile(RetracedClassMember frame, String sourceFile);

    List<? extends RetracedMethod> getOuterFrames();
  }
}
