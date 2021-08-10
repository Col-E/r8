// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexMethod;
import java.util.function.Supplier;

public class MethodStateCollection {

  public void joinMethodState(DexMethod method, Supplier<MethodState> state) {
    // TODO(b/190154391): Do not attempt to compute the method state using the provided supplier
    //  if the state for the given method is already unknown.
    throw new Unimplemented();
  }
}
