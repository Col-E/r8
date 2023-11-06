// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.function.Consumer;
import java.util.stream.Stream;

@KeepForApi
public interface RetraceUnknownJsonMappingInformationResult {

  /** Basic operation over 'elements' which represent a possible non-ambiguous retracing. */
  Stream<RetraceUnknownMappingInformationElement> stream();

  /** Short-hand for iterating the elements. */
  default void forEach(Consumer<RetraceUnknownMappingInformationElement> action) {
    stream().forEach(action);
  }
}
