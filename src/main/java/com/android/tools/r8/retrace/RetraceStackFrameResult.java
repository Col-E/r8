// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link RetraceStackFrameResult} is collection of retraced frames from an input frame + context.
 *
 * <p>It is guaranteed to be non-ambiguous. It can have more than one frame if it is an inline or an
 * outline expansion. It can be empty, fx. if the frames are compiler synthesized.
 */
@KeepForApi
public interface RetraceStackFrameResult<T> {

  /**
   * Get a list of retraced frames.
   *
   * <p>The first reported result is the innermost frame.
   *
   * @return the list of retraced frames.
   */
  List<T> getResult();

  /**
   * Consume retraced frames.
   *
   * <p>The first reported result is the innermost frame.
   *
   * @param consumer The consumer to receive results.
   */
  void forEach(Consumer<T> consumer);

  int size();

  T get(int i);

  boolean isEmpty();
}
