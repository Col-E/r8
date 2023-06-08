// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * RetraceStackFrameAmbiguousResult is a potentially empty collection of RetraceStackFrameResult.
 *
 * <p>A result is ambiguous if the mapping file contains information that is ambiguous. It can be
 * empty if the frame is a compiler synthesized frame. See
 * https://r8.googlesource.com/r8/+/main/doc/retrace.md for what information Retrace uses to discard
 * frames.
 */
@Keep
public interface RetraceStackFrameAmbiguousResult<T> {

  /**
   * Predicate on this result being ambiguous.
   *
   * <p>The {@link RetraceStackFrameAmbiguousResult} is ambiguous if size of {@link
   * #getAmbiguousResult()} is greater than 1.
   *
   * @return true if the result is ambiguous.
   */
  boolean isAmbiguous();

  /**
   * Get a list of potential ambiguous results.
   *
   * <p>If there is only a single RetraceStackFrameResult it is non-ambiguous. Note that it can also
   * be empty, which implies that the result expands unambiguously to an empty stack section.
   *
   * @return The list of potential ambiguous results.
   */
  List<RetraceStackFrameResult<T>> getAmbiguousResult();

  /**
   * Consume potential ambiguous results.
   *
   * <p>If there is only a single RetraceStackFrameResult it is non-ambiguous. Note that it can also
   * be empty.
   *
   * @param consumer The consumer to receive results.
   */
  void forEach(Consumer<RetraceStackFrameResult<T>> consumer);

  /**
   * Consume potential ambiguous results.
   *
   * <p>If there is only a single RetraceStackFrameResult it non-ambiguous. Note that it can also be
   * empty.
   *
   * @param consumer The consumer to receive the index and result.
   */
  void forEachWithIndex(BiConsumer<RetraceStackFrameResult<T>, Integer> consumer);

  int size();

  boolean isEmpty();

  RetraceStackFrameResult<T> get(int i);
}
