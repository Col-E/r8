// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

/** Two value continuation value to indicate the continuation of a loop/traversal. */
public enum TraversalContinuation {
  CONTINUE,
  BREAK;

  public final boolean shouldBreak() {
    return this == BREAK;
  }

  public final boolean shouldContinue() {
    return this == CONTINUE;
  }
}
