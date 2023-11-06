// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Client supplied checker to allow cancelling a compilation before completion. */
@KeepForApi
public interface CancelCompilationChecker {

  /**
   * Callback that is called periodically by the compiler to check if the current compilation should
   * be cancelled before completion. The method should return true or false. The behavior of
   * throwing an exception from this method will also abort the compilation but the compiler will
   * treat such cases as an unexpected runtime failure and not as an expected client cancellation.
   *
   * <p>If this method returns true at any point before compilation has successfully completed, then
   * the compiler will exit with a {@link CompilationFailedException}. It is *not* possible to
   * cancel the cancellation.
   *
   * <p>This method may be called from multiple compiler threads. It is up to the client to ensure
   * thread safety in determining if compilation should be cancelled.
   *
   * @return True if the compilation should be cancelled, otherwise false.
   */
  boolean cancel();
}
