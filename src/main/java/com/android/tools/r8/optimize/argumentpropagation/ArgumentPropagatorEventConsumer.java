// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;

public interface ArgumentPropagatorEventConsumer {

  static ArgumentPropagatorEventConsumer emptyConsumer() {
    return new ArgumentPropagatorEventConsumer() {
      @Override
      public void acceptCodeScannerResult(MethodStateCollectionByReference methodStates) {
        // Intentionally empty.
      }
    };
  }

  void acceptCodeScannerResult(MethodStateCollectionByReference methodStates);

  default ArgumentPropagatorEventConsumer andThen(
      ArgumentPropagatorEventConsumer nextEventConsumer) {
    ArgumentPropagatorEventConsumer self = this;
    return new ArgumentPropagatorEventConsumer() {
      @Override
      public void acceptCodeScannerResult(MethodStateCollectionByReference methodStates) {
        self.acceptCodeScannerResult(methodStates);
        nextEventConsumer.acceptCodeScannerResult(methodStates);
      }
    };
  }
}
