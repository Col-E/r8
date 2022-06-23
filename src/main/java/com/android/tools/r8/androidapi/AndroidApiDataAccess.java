// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.lightir.ByteUtils.unsetBitAtIndex;
import static com.android.tools.r8.utils.ZipUtils.getOffsetOfResourceInZip;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

  public static boolean isApiDatabaseEntry(String entry) {
    return RESOURCE_NAME.equals(entry);
  }

  private static class PositionAndLength {

    private static final PositionAndLength EMPTY = new PositionAndLength(0, 0);

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
      if ((position < 0 && length > 0) || (position > 0 && length == 0)) {
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

  public static AndroidApiDataAccess create(
      InternalOptions options, DiagnosticsHandler diagnosticsHandler) {
    URL resource = AndroidApiDataAccess.class.getClassLoader().getResource(RESOURCE_NAME);
    if (resource == null) {
      diagnosticsHandler.warning(
          new StringDiagnostic("Could not find the api database at " + RESOURCE_NAME));
      return new AndroidApiDataAccessNoBacking();
    }
    if (options.apiModelingOptions().useMemoryMappedByteBuffer) {
      try {
        // The resource is encoded as protocol and a path, where we should have one of either:
        // protocol: file, path: <path-to-file>
        // protocol: jar, path: file:<path-to-jar>!/<resource-name-in-jar>
        if (resource.getProtocol().equals("file")) {
          return getDataAccessFromPathAndOffset(Paths.get(resource.toURI()), 0);
        } else if (resource.getProtocol().equals("jar") && resource.getPath().startsWith("file:")) {
          // The path is on form 'file:<path-to-jar>!/<resource-name-in-jar>
          JarURLConnection jarUrl = (JarURLConnection) resource.openConnection();
          File jarFile = new File(jarUrl.getJarFileURL().getFile());
          String databaseEntry = jarUrl.getEntryName();
          long offsetInJar = getOffsetOfResourceInZip(jarFile, databaseEntry);
          if (offsetInJar > 0) {
            return getDataAccessFromPathAndOffset(jarFile.toPath(), offsetInJar);
          }
        }
        // On older DEX platforms creating a new byte channel may fail:
        // Error: java.lang.NoSuchMethodError: No static method newByteChannel(Ljava/nio/file/Path;
        // [Ljava/nio/file/OpenOption;)Ljava/nio/channels/SeekableByteChannel;
        // in class Ljava/nio/file/Files
      } catch (Exception | NoSuchMethodError e) {
        diagnosticsHandler.warning(new ExceptionDiagnostic(e));
      }
      diagnosticsHandler.warning(
          new StringDiagnostic(
              "Unable to use a memory mapped byte buffer to access the api database. Falling back"
                  + " to loading the database into program which requires more memory"));
    }
    try (InputStream apiInputStream =
        AndroidApiDataAccess.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
      if (apiInputStream == null) {
        diagnosticsHandler.warning(
            new StringDiagnostic("Could not open the api database at " + RESOURCE_NAME));
        return new AndroidApiDataAccessNoBacking();
      }
      return new AndroidApiDataAccessInMemory(ByteStreams.toByteArray(apiInputStream));
    } catch (IOException e) {
      diagnosticsHandler.warning(new ExceptionDiagnostic(e));
      return new AndroidApiDataAccessNoBacking();
    }
  }

  private static AndroidApiDataAccessByteMapped getDataAccessFromPathAndOffset(
      Path path, long offset) throws IOException {
    FileChannel fileChannel = (FileChannel) Files.newByteChannel(path, StandardOpenOption.READ);
    MappedByteBuffer mappedByteBuffer =
        fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, fileChannel.size() - offset);
    // Ensure that we can run on JDK 8 by using the CompatByteBuffer.
    return new AndroidApiDataAccessByteMapped(new CompatByteBuffer(mappedByteBuffer));
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

  /** The start of the constant pool */
  public static int constantPoolOffset() {
    return 4;
  }

  /** The start of the constant pool hash map. */
  public static int constantPoolHashMapOffset(int constantPoolSize) {
    return (constantPoolSize * constantPoolEntrySize()) + constantPoolOffset();
  }

  /** The start of the api level hash map. */
  public static int apiLevelHashMapOffset(int constantPoolSize) {
    int constantPoolHashMapSize =
        (1 << entrySizeInBitsForConstantPoolMap()) * constantPoolMapEntrySize();
    return constantPoolHashMapOffset(constantPoolSize) + constantPoolHashMapSize;
  }

  /** The start of the payload section. */
  public static int payloadOffset(int constantPoolSize) {
    int apiLevelSize = (1 << entrySizeInBitsForApiLevelMap()) * apiLevelHashMapEntrySize();
    return apiLevelHashMapOffset(constantPoolSize) + apiLevelSize;
  }

  /** The actual byte index of the constant pool index. */
  public int constantPoolIndexOffset(int index) {
    return constantPoolOffset() + (index * constantPoolEntrySize());
  }

  /** The actual byte index of the constant pool hash key. */
  protected int constantPoolHashMapIndexOffset(int hash) {
    return constantPoolHashMapOffset(getConstantPoolSize()) + (hash * constantPoolMapEntrySize());
  }

  /** The actual byte index of the api hash key. */
  protected int apiLevelHashMapIndexOffset(int hash) {
    return apiLevelHashMapOffset(getConstantPoolSize()) + (hash * apiLevelHashMapEntrySize());
  }

  static int readIntFromOffset(byte[] data, int offset) {
    return Ints.fromBytes(data[offset], data[offset + 1], data[offset + 2], data[offset + 3]);
  }

  static int readShortFromOffset(byte[] data, int offset) {
    return Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[offset], data[offset + 1]);
  }

  private int constantPoolSizeCache = -1;

  abstract int readConstantPoolSize();

  abstract PositionAndLength readPositionAndLength(int offset);

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
        readPositionAndLength(constantPoolHashMapIndexOffset(constantPoolHash(string)));
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
          payloadOffset(getConstantPoolSize()) + position,
          length,
          string.content,
          this::isConstantPoolEntry);
    }
    return -1;
  }

  public boolean isConstantPoolEntry(int index, byte[] value) {
    PositionAndLength constantPoolPayloadOffset =
        readPositionAndLength(constantPoolIndexOffset(index));
    if (constantPoolPayloadOffset.isEmpty()) {
      return false;
    }
    if (value.length != constantPoolPayloadOffset.getLength()) {
      return false;
    }
    return payloadHasConstantPoolValue(
        payloadOffset(getConstantPoolSize()) + constantPoolPayloadOffset.getPosition(),
        constantPoolPayloadOffset.getLength(),
        value);
  }

  public byte getApiLevelForReference(byte[] serialized, DexReference reference) {
    PositionAndLength apiLevelPayloadOffset =
        readPositionAndLength(apiLevelHashMapIndexOffset(apiLevelHash(reference)));
    if (apiLevelPayloadOffset.isEmpty()) {
      return 0;
    }
    return readApiLevelForPayloadOffset(
        payloadOffset(getConstantPoolSize()) + apiLevelPayloadOffset.getPosition(),
        apiLevelPayloadOffset.getLength(),
        serialized);
  }

  public boolean isNoBacking() {
    return false;
  }

  public static class AndroidApiDataAccessByteMapped extends AndroidApiDataAccess {

    private final CompatByteBuffer mappedByteBuffer;

    public AndroidApiDataAccessByteMapped(CompatByteBuffer mappedByteBuffer) {
      this.mappedByteBuffer = mappedByteBuffer;
    }

    @Override
    int readConstantPoolSize() {
      return mappedByteBuffer.getInt(0);
    }

    @Override
    public PositionAndLength readPositionAndLength(int offset) {
      return PositionAndLength.create(
          mappedByteBuffer.getInt(offset), mappedByteBuffer.getShort(offset + 4));
    }

    @Override
    boolean payloadHasConstantPoolValue(int offset, int length, byte[] value) {
      assert length == value.length;
      mappedByteBuffer.position(offset);
      for (byte expected : value) {
        if (expected != mappedByteBuffer.get()) {
          return false;
        }
      }
      return true;
    }

    @Override
    int payloadContainsConstantPoolValue(
        int offset, int length, byte[] value, BiPredicate<Integer, byte[]> predicate) {
      for (int i = offset; i < offset + length; i += 2) {
        // Do not use mappedByteBuffer.getShort() since that will add the sign.
        int index =
            Ints.fromBytes(
                ZERO_BYTE, ZERO_BYTE, mappedByteBuffer.get(i), mappedByteBuffer.get(i + 1));
        if (predicate.test(index, value)) {
          return index;
        }
      }
      return -1;
    }

    @Override
    byte readApiLevelForPayloadOffset(int offset, int length, byte[] value) {
      int currentOffset = offset;
      while (currentOffset < offset + length) {
        // Read the length
        int lengthOfEntry =
            Ints.fromBytes(
                ZERO_BYTE,
                ZERO_BYTE,
                mappedByteBuffer.get(currentOffset),
                mappedByteBuffer.get(currentOffset + 1));
        int startPosition = currentOffset + 2;
        if (value.length == lengthOfEntry
            && payloadHasConstantPoolValue(startPosition, lengthOfEntry, value)) {
          return mappedByteBuffer.get(startPosition + lengthOfEntry);
        }
        // Advance our current position + length of entry + api level.
        currentOffset = startPosition + lengthOfEntry + 1;
      }
      return -1;
    }
  }

  public static class AndroidApiDataAccessInMemory extends AndroidApiDataAccess {

    private final byte[] data;

    private AndroidApiDataAccessInMemory(byte[] data) {
      this.data = data;
    }

    @Override
    public int readConstantPoolSize() {
      return readIntFromOffset(data, 0);
    }

    @Override
    PositionAndLength readPositionAndLength(int offset) {
      return PositionAndLength.create(data, offset);
    }

    @Override
    boolean payloadHasConstantPoolValue(int offset, int length, byte[] value) {
      if (value.length != length) {
        return false;
      }
      for (int i = 0; i < length; i++) {
        if (value[i] != data[i + offset]) {
          return false;
        }
      }
      return true;
    }

    @Override
    int payloadContainsConstantPoolValue(
        int offset, int length, byte[] value, BiPredicate<Integer, byte[]> predicate) {
      if (data.length < length) {
        return -1;
      }
      for (int i = offset; i < offset + length; i += 2) {
        int index = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[i], data[i + 1]);
        if (predicate.test(index, value)) {
          return index;
        }
      }
      return -1;
    }

    @Override
    byte readApiLevelForPayloadOffset(int offset, int length, byte[] value) {
      int index = offset;
      while (index < offset + length) {
        // Read size of entry
        int lengthOfEntry = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, data[index], data[index + 1]);
        int startIndex = index + 2;
        int endIndex = startIndex + lengthOfEntry;
        if (payloadHasConstantPoolValue(startIndex, lengthOfEntry, value)) {
          return data[endIndex];
        }
        index = endIndex + 1;
      }
      return 0;
    }
  }

  public static class AndroidApiDataAccessNoBacking extends AndroidApiDataAccess {

    @Override
    int readConstantPoolSize() {
      throw new Unreachable();
    }

    @Override
    PositionAndLength readPositionAndLength(int offset) {
      throw new Unreachable();
    }

    @Override
    boolean payloadHasConstantPoolValue(int offset, int length, byte[] value) {
      throw new Unreachable();
    }

    @Override
    int payloadContainsConstantPoolValue(
        int offset, int length, byte[] value, BiPredicate<Integer, byte[]> predicate) {
      throw new Unreachable();
    }

    @Override
    byte readApiLevelForPayloadOffset(int offset, int length, byte[] value) {
      throw new Unreachable();
    }

    @Override
    public boolean isNoBacking() {
      return true;
    }
  }
}
