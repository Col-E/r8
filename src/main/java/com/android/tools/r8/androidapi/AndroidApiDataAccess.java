// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.lightir.ByteUtils.unsetBitAtIndex;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.function.BiPredicate;

/**
 * Implements low-level access methods for seeking on top of the database file defined by {@code
 * AndroidApiLevelHashingDatabaseImpl} where a description of the format can also be found.
 */
public abstract class AndroidApiDataAccess {

  private static final String RESOURCE_NAME = "resources/new_api_database.ser";
  private static final int ENTRY_SIZE_IN_BITS_FOR_CONSTANT_POOL_MAP = 17;
  private static final int ENTRY_SIZE_IN_BITS_FOR_API_MAP = 18;
  // The payload offset is an offset into the payload defined by an integer and a length defined by
  // a short.
  private static final int PAYLOAD_OFFSET_WITH_LENGTH = 4 + 2;
  private static final byte ZERO_BYTE = (byte) 0;

  private static class PositionAndLength {

    private static PositionAndLength EMPTY = new PositionAndLength(0, 0);

    private final int position;
    private final int length;

    private PositionAndLength(int position, int length) {
      this.position = position;
      this.length = length;
    }

    public static PositionAndLength create(int position, int length) {
      if (position == 0 && length == 0) {
        return EMPTY;
      }
      if ((position < 0 && length != 0) || (position > 0 && length == 0)) {
        assert false : "Unexpected position and length";
        return EMPTY;
      }
      return new PositionAndLength(position, length);
    }

    public static PositionAndLength create(byte[] data, int offset) {
      return create(readIntFromOffset(data, offset), readShortFromOffset(data, offset + 4));
    }

    public int getPosition() {
      return position;
    }

    public int getLength() {
      return length;
    }

    public boolean isEmpty() {
      return this == EMPTY;
    }
  }

  public static int entrySizeInBitsForConstantPoolMap() {
    return ENTRY_SIZE_IN_BITS_FOR_CONSTANT_POOL_MAP;
  }

  public static int entrySizeInBitsForApiLevelMap() {
    return ENTRY_SIZE_IN_BITS_FOR_API_MAP;
  }

  public static int apiLevelHash(DexReference reference) {
    int entrySize = entrySizeInBitsForApiLevelMap();
    int size = 1 << (entrySize - 1);
    return (reference.hashCode() % size) + size;
  }

  public static int constantPoolHash(DexString string) {
    int entrySize = entrySizeInBitsForConstantPoolMap();
    int size = 1 << (entrySize - 1);
    return (string.hashCode() % size) + size;
  }

  static int constantPoolEntrySize() {
    return PAYLOAD_OFFSET_WITH_LENGTH;
  }

  static int constantPoolMapEntrySize() {
    return PAYLOAD_OFFSET_WITH_LENGTH;
  }

  static int apiLevelHashMapEntrySize() {
    return PAYLOAD_OFFSET_WITH_LENGTH;
  }

  static int constantPoolOffset() {
    return 4;
  }

  static int constantPoolHashMapOffset(int constantPoolSize) {
    return (constantPoolSize * constantPoolEntrySize()) + constantPoolOffset();
  }

  static int apiLevelHashMapOffset(int constantPoolSize) {
    int constantPoolHashMapSize =
        (1 << entrySizeInBitsForConstantPoolMap()) * constantPoolMapEntrySize();
    return constantPoolHashMapOffset(constantPoolSize) + constantPoolHashMapSize;
  }

  static int payloadOffset(int constantPoolSize) {
    int apiLevelSize = (1 << entrySizeInBitsForApiLevelMap()) * apiLevelHashMapEntrySize();
    return apiLevelHashMapOffset(constantPoolSize) + apiLevelSize;
  }

  static int readIntFromOffset(byte[] data, int offset) {
    return Ints.fromBytes(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]);
  }

  static int readShortFromOffset(byte[] data, int offset) {
    return Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[offset], data[offset + 1]);
  }

  private int constantPoolSizeCache = -1;

  abstract int readConstantPoolSize();

  abstract PositionAndLength getConstantPoolPayloadOffset(int index);

  abstract PositionAndLength getConstantPoolHashMapPayloadOffset(int hash);

  abstract PositionAndLength getApiLevelHashMapPayloadOffset(int hash);

  abstract boolean payloadHasConstantPoolValue(int offset, int length, byte[] value);

  abstract int payloadContainsConstantPoolValue(
      int offset, int length, byte[] value, BiPredicate<Integer, byte[]> predicate);

  abstract byte readApiLevelForPayloadOffset(int offset, int length, byte[] value);

  public int getConstantPoolSize() {
    if (constantPoolSizeCache == -1) {
      constantPoolSizeCache = readConstantPoolSize();
    }
    return constantPoolSizeCache;
  }

  /** When the first bit is set (position < 0) then there is a single unique result for the hash. */
  public static boolean isUniqueConstantPoolEntry(int position) {
    return position < 0;
  }

  /**
   * If the position defines a unique result, the first byte is has the first bit set to 1 (making
   * it negative) and the actual index specified in the least significant two bytes.
   */
  public static int getConstantPoolIndexFromUniqueConstantPoolEntry(int position) {
    assert isUniqueConstantPoolEntry(position);
    return unsetBitAtIndex(position, 32);
  }

  public int getConstantPoolIndex(DexString string) {
    PositionAndLength constantPoolIndex =
        getConstantPoolHashMapPayloadOffset(constantPoolHash(string));
    if (constantPoolIndex.isEmpty()) {
      return -1;
    }
    int position = constantPoolIndex.getPosition();
    int length = constantPoolIndex.getLength();
    if (isUniqueConstantPoolEntry(position)) {
      int nonTaggedPosition = getConstantPoolIndexFromUniqueConstantPoolEntry(position);
      if (isConstantPoolEntry(nonTaggedPosition, string.content)) {
        return nonTaggedPosition;
      }
    } else {
      assert length > 0;
      return payloadContainsConstantPoolValue(
          position, length, string.content, this::isConstantPoolEntry);
    }
    return -1;
  }

  public boolean isConstantPoolEntry(int index, byte[] value) {
    PositionAndLength constantPoolPayloadOffset = getConstantPoolPayloadOffset(index);
    if (constantPoolPayloadOffset.isEmpty()) {
      return false;
    }
    return payloadHasConstantPoolValue(
        constantPoolPayloadOffset.getPosition(), constantPoolPayloadOffset.getLength(), value);
  }

  public byte getApiLevelForReference(byte[] serialized, DexReference reference) {
    PositionAndLength apiLevelPayloadOffset =
        getApiLevelHashMapPayloadOffset(apiLevelHash(reference));
    if (apiLevelPayloadOffset.isEmpty()) {
      return 0;
    }
    return readApiLevelForPayloadOffset(
        apiLevelPayloadOffset.getPosition(), apiLevelPayloadOffset.getLength(), serialized);
  }

  public static byte findApiForReferenceHelper(byte[] data, int offset, int length, byte[] value) {
    int index = offset;
    while (index < offset + length) {
      // Read size of entry
      int lengthOfEntry = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[index], data[index + 1]);
      int startIndex = index + 2;
      int endIndex = startIndex + lengthOfEntry;
      if (isSerializedDescriptor(value, data, startIndex, lengthOfEntry)) {
        return data[endIndex];
      }
      index = endIndex + 1;
    }
    return 0;
  }

  protected static boolean isSerializedDescriptor(
      byte[] serialized, byte[] candidate, int offset, int length) {
    if (serialized.length != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      if (serialized[i] != candidate[i + offset]) {
        return false;
      }
    }
    return true;
  }

  public static class AndroidApiDataAccessInMemory extends AndroidApiDataAccess {

    private final byte[] data;

    private AndroidApiDataAccessInMemory(byte[] data) {
      this.data = data;
    }

    public static AndroidApiDataAccessInMemory create() {
      byte[] data;
      try (InputStream apiInputStream =
          AndroidApiDataAccess.class.getClassLoader().getResourceAsStream(RESOURCE_NAME); ) {
        if (apiInputStream == null) {
          URL resource = AndroidApiDataAccess.class.getClassLoader().getResource(RESOURCE_NAME);
          throw new CompilationError("Could not find the api database at: " + resource);
        }
        data = ByteStreams.toByteArray(apiInputStream);
      } catch (IOException e) {
        throw new CompilationError("Could not read the api database.", e);
      }
      return new AndroidApiDataAccessInMemory(data);
    }

    @Override
    public int readConstantPoolSize() {
      return readIntFromOffset(data, 0);
    }

    @Override
    PositionAndLength getConstantPoolPayloadOffset(int index) {
      int offset = constantPoolOffset() + (index * constantPoolEntrySize());
      return PositionAndLength.create(data, offset);
    }

    @Override
    PositionAndLength getConstantPoolHashMapPayloadOffset(int hash) {
      int offset =
          constantPoolHashMapOffset(getConstantPoolSize()) + (hash * constantPoolMapEntrySize());
      return PositionAndLength.create(data, offset);
    }

    @Override
    PositionAndLength getApiLevelHashMapPayloadOffset(int hash) {
      int offset =
          apiLevelHashMapOffset(getConstantPoolSize()) + (hash * apiLevelHashMapEntrySize());
      return PositionAndLength.create(data, offset);
    }

    @Override
    boolean payloadHasConstantPoolValue(int offset, int length, byte[] value) {
      return isSerializedDescriptor(
          value, data, payloadOffset(getConstantPoolSize()) + offset, length);
    }

    @Override
    int payloadContainsConstantPoolValue(
        int offset, int length, byte[] value, BiPredicate<Integer, byte[]> predicate) {
      int payloadOffset = payloadOffset(getConstantPoolSize());
      int startInPayload = payloadOffset + offset;
      int endInPayload = startInPayload + length;
      if (data.length < endInPayload) {
        return -1;
      }
      for (int i = startInPayload; i < endInPayload; i += 2) {
        int index = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[i], data[i + 1]);
        if (predicate.test(index, value)) {
          return index;
        }
      }
      return -1;
    }

    @Override
    byte readApiLevelForPayloadOffset(int offset, int length, byte[] value) {
      return findApiForReferenceHelper(
          data, payloadOffset(getConstantPoolSize()) + offset, length, value);
    }
  }
}
