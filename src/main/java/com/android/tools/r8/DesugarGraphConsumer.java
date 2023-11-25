// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;

/** Consumer for receiving dependency edges for desugaring. */
@KeepForApi
public interface DesugarGraphConsumer {

  /**
   * Callback indicating that the {@code node} is a program input which is part of the current
   * compilation unit for desugaring.
   *
   * <p>Note: this callback is guaranteed to be called on every *program-input* origin that could be
   * passed as a {@code dependent} in a callback to {@code accept(Orign dependent, Origin
   * dependency)}. It is also guaranteed to be called before any such call. In effect, this callback
   * will receive the complete set of program-input origins for the compilation unit that is being
   * desugared and it can reliably be used to remove any existing and potentially stale edges
   * pertaining to those origins from a dependency graph maintained in the client.
   *
   * <p>Note: this will not receive a callback for classpath origins.
   *
   * <p>Note: this callback may be called on multiple threads.
   *
   * <p>Note: this callback places no guarantees on order of calls or on duplicate calls.
   *
   * @param node Origin of code that is part of the program input in the compilation unit.
   */
  default void acceptProgramNode(Origin node) {
    // Default behavior ignores the node callbacks.
  }

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
