// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Keep
public interface RetraceStackFrameAmbiguousResult<T> {

  boolean isAmbiguous();

  List<RetraceStackFrameResult<T>> getAmbiguousResult();

  void forEach(Consumer<RetraceStackFrameResult<T>> consumer);

  void forEachWithIndex(BiConsumer<RetraceStackFrameResult<T>, Integer> consumer);

  int size();

  boolean isEmpty();

  RetraceStackFrameResult<T> get(int i);
}
