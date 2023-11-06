// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Levels of diagnostics messages reported by the compiler. */
@KeepForApi
public enum DiagnosticsLevel {
  ERROR,
  WARNING,
  INFO,
  NONE
}
