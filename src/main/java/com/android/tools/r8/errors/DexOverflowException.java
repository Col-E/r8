// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.CompilationException;

/**
 * Signals when there were too many items to fit in a given dex file.
 */
public class DexOverflowException extends CompilationException {

  protected DexOverflowException() {
    super();
  }

  public DexOverflowException(String message) {
    super(message);
  }
}
