// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.Collections;
import java.util.Set;

public final class KeepOptions {

  public enum KeepOption {
    SHRINKING,
    OPTIMIZING,
    OBFUSCATING,
    ACCESS_MODIFING,
  }

  public static KeepOptions keepAll() {
    if (ALLOW_NONE_INSTANCE == null) {
      ALLOW_NONE_INSTANCE = new KeepOptions(true, Collections.emptySet());
    }
    return ALLOW_NONE_INSTANCE;
  }

  private static KeepOptions ALLOW_NONE_INSTANCE = null;

  private final boolean allowIfSet;
  private final Set<KeepOption> options;

  private KeepOptions(boolean allowIfSet, Set<KeepOption> options) {
    this.allowIfSet = allowIfSet;
    this.options = options;
  }

  public boolean allow(KeepOption option) {
    return options.contains(option) == allowIfSet;
  }
}
