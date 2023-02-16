// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

/** Most primitive interface for providing consumer to the {@link LirWriter}. */
public interface ByteWriter {

  /** Put a byte value, must represent an unsigned byte (int between 0 and 255). */
  void put(int u1);
}
