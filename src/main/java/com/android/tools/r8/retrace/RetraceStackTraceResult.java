// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;
import java.util.function.Consumer;

/**
 * A collection of multiple retraced frames.
 *
 * <p>The number of elements is the same as the number of input frames.
 */
@Keep
public interface RetraceStackTraceResult<T> extends RetraceResultWithContext {

  /**
   * Get a list of results where each element is the result of retracing the original frame.
   *
   * @return the list of results.
   */
  List<RetraceStackFrameAmbiguousResult<T>> getResult();

  /**
   * Iterate over a potential empty list of ambiguous results.
   *
   * @param consumer The consumer to accept an ambiguous result.
   */
  void forEach(Consumer<RetraceStackFrameAmbiguousResult<T>> consumer);

  /**
   * Predicate to check if the result is empty.
   *
   * <p>The result is only empty when no original frames were given.
   *
   * @return true if the result is empty.
   */
  boolean isEmpty();
}
