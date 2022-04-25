// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import it.unimi.dsi.fastutil.bytes.ByteIterator;

/** Simple implementation of an iterator over a primitive byte array. */
public class ByteArrayIterator implements ByteIterator {

  private final int size;
  private final byte[] buffer;
  private int index = 0;

  public ByteArrayIterator(byte[] bytes) {
    size = bytes.length;
    buffer = bytes;
  }

  @Override
  public boolean hasNext() {
    return index < size;
  }

  @Override
  public byte nextByte() {
    return buffer[index++];
  }

  @Override
  public Byte next() {
    return nextByte();
  }

  @Override
  public int skip(int i) {
    int actual = index + i <= size ? i : size - index;
    index += actual;
    return actual;
  }
}
