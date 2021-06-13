// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.utils.collections.ProgramMethodSet;

/**
 * An abstraction of optimizations that require post processing of methods.
 */
public interface PostOptimization {

  /** @return a set of methods that need post processing. */
  ProgramMethodSet methodsToRevisit();
}
