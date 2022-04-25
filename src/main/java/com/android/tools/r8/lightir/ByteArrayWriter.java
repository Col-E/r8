// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import java.io.ByteArrayOutputStream;

/** Simple implementation to construct a primitive byte array. */
public class ByteArrayWriter implements ByteWriter {

  // Backing is just the default capacity reallocating java byte array.
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  @Override
  public void put(int u1) {
    assert ByteUtils.isU1(u1);
    buffer.write(u1);
  }

  public byte[] toByteArray() {
    return buffer.toByteArray();
  }
}
