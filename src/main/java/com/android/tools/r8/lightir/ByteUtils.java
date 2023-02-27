// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import it.unimi.dsi.fastutil.bytes.ByteIterator;

/** Simple utilities for byte encodings. */
public class ByteUtils {

  public static boolean isU1(int value) {
    return (0 <= value) && (value <= 0xFF);
  }

  // Lossy truncation of an integer value to its lowest byte.
  private static int truncateToU1(int value) {
    return value & 0xFF;
  }

  public static int ensureU1(int value) {
    assert isU1(value);
    return truncateToU1(value);
  }

  public static int fromU1(byte value) {
    return value & 0xFF;
  }

  public static int intEncodingSize(int value) {
    return 4;
  }

  public static void writeEncodedInt(int value, ByteWriter writer) {
    assert 4 == intEncodingSize(value);
    writer.put(truncateToU1(value >> 24));
    writer.put(truncateToU1(value >> 16));
    writer.put(truncateToU1(value >> 8));
    writer.put(truncateToU1(value));
  }

  public static int readEncodedInt(ByteIterator iterator) {
    assert 4 == intEncodingSize(0);
    int value = truncateToU1(iterator.nextByte()) << 24;
    value |= truncateToU1(iterator.nextByte()) << 16;
    value |= truncateToU1(iterator.nextByte()) << 8;
    value |= truncateToU1(iterator.nextByte());
    return value;
  }

  public static boolean isU2(int value) {
    return (value >= 0) && (value <= 0xFFFF);
  }

  public static int unsetBitAtIndex(int value, int index) {
    return value & ~(1 << (index - 1));
  }

  public static int setBitAtIndex(int value, int index) {
    return value | (1 << (index - 1));
  }
}
