// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import it.unimi.dsi.fastutil.bytes.ByteIterator;

/** Simple utilities for byte encodings. */
public class ByteUtils {

  public static final int MAX_U1 = 0xFF;
  public static final int MAX_U2 = 0xFFFF;

  public static boolean isU1(int value) {
    return (0 <= value) && (value <= MAX_U1);
  }

  // Lossy truncation of an integer value to its lowest byte.
  private static int truncateToU1(int value) {
    return value & MAX_U1;
  }

  private static int truncateToU1(long value) {
    return (int) value & MAX_U1;
  }

  public static int ensureU1(int value) {
    assert isU1(value);
    return truncateToU1(value);
  }

  public static int fromU1(byte value) {
    return value & MAX_U1;
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

  public static int longEncodingSize(long value) {
    return 8;
  }

  public static void writeEncodedLong(long value, ByteWriter writer) {
    assert 8 == longEncodingSize(value);
    writer.put(truncateToU1(value >> 56));
    writer.put(truncateToU1(value >> 48));
    writer.put(truncateToU1(value >> 40));
    writer.put(truncateToU1(value >> 32));
    writer.put(truncateToU1(value >> 24));
    writer.put(truncateToU1(value >> 16));
    writer.put(truncateToU1(value >> 8));
    writer.put(truncateToU1(value));
  }

  public static long readEncodedLong(ByteIterator iterator) {
    assert 8 == longEncodingSize(0);
    long value = ((long) truncateToU1(iterator.nextByte())) << 56;
    value |= ((long) truncateToU1(iterator.nextByte())) << 48;
    value |= ((long) truncateToU1(iterator.nextByte())) << 40;
    value |= ((long) truncateToU1(iterator.nextByte())) << 32;
    value |= ((long) truncateToU1(iterator.nextByte())) << 24;
    value |= ((long) truncateToU1(iterator.nextByte())) << 16;
    value |= ((long) truncateToU1(iterator.nextByte())) << 8;
    value |= truncateToU1(iterator.nextByte());
    return value;
  }

  public static boolean isU2(int value) {
    return (value >= 0) && (value <= MAX_U2);
  }

  private static int truncateToU2(int value) {
    return value & MAX_U2;
  }

  public static int ensureU2(int value) {
    assert isU2(value);
    return truncateToU2(value);
  }

  public static int unsetBitAtIndex(int value, int index) {
    return value & ~(1 << (index - 1));
  }

  public static int setBitAtIndex(int value, int index) {
    return value | (1 << (index - 1));
  }
}
