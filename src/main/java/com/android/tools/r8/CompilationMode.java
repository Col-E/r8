// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Compilation mode. */
@KeepForApi
public enum CompilationMode {
  /** Preserves debugging information during compilation, eg, line-numbers and locals. */
  DEBUG,
  /** Strips debugging information that cannot affect stack traces. */
  RELEASE;

  public boolean isDebug() {
    return this == DEBUG;
  }

  public boolean isRelease() {
    return this == RELEASE;
  }
}
