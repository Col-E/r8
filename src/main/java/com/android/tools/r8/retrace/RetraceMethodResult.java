// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public interface RetraceMethodResult {

  RetraceFrameResult narrowByPosition(int position);

  Stream<Element> stream();

  RetraceMethodResult forEach(Consumer<Element> resultConsumer);

  boolean isAmbiguous();

  @Keep
  interface Element {

    boolean isUnknown();

    RetracedMethod getRetracedMethod();

    RetraceMethodResult getRetraceMethodResult();

    RetraceClassResult.Element getClassElement();

    RetraceSourceFileResult retraceSourceFile(String sourceFile);
  }
}
