// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public interface RetraceTypeResult {

  Stream<Element> stream();

  RetraceTypeResult forEach(Consumer<Element> resultConsumer);

  boolean isAmbiguous();

  @Keep
  interface Element {

    RetracedType getType();
  }
}
