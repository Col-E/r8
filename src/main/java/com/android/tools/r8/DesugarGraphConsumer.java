// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;

/** Consumer for receiving dependency edges for desugaring. */
@KeepForSubclassing
public interface DesugarGraphConsumer {

  /**
   * Callback indicating that code originating from {@code dependency} is needed to correctly
   * desugar code originating from {@code dependent}.
   *
   * <p>Note: this callback may be called on multiple threads.
   *
   * <p>Note: this callback places no guarantees on order of calls or on duplicate calls.
   *
   * @param dependent Origin of code that is dependent on code in {@code dependency}.
   * @param dependency Origin of code that is a dependency to compile {@code dependent}.
   */
  void accept(Origin dependent, Origin dependency);

  /**
   * Callback indicating no more dependency edges for the active compilation unit.
   *
   * <p>Note: this callback places no other guarantees on number of calls or on which threads.
   */
  void finished();
}
