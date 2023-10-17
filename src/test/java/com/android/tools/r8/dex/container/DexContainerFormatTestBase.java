// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container;

import static com.android.tools.r8.dex.Constants.CHECKSUM_OFFSET;
import static com.android.tools.r8.dex.Constants.CONTAINER_OFF_OFFSET;
import static com.android.tools.r8.dex.Constants.CONTAINER_SIZE_OFFSET;
import static com.android.tools.r8.dex.Constants.DATA_OFF_OFFSET;
import static com.android.tools.r8.dex.Constants.DATA_SIZE_OFFSET;
import static com.android.tools.r8.dex.Constants.DEX_MAGIC_SIZE;
import static com.android.tools.r8.dex.Constants.FILE_SIZE_OFFSET;
import static com.android.tools.r8.dex.Constants.HEADER_SIZE_OFFSET;
import static com.android.tools.r8.dex.Constants.MAP_OFF_OFFSET;
import static com.android.tools.r8.dex.Constants.SIGNATURE_OFFSET;
import static com.android.tools.r8.dex.Constants.STRING_IDS_OFF_OFFSET;
import static com.android.tools.r8.dex.Constants.STRING_IDS_SIZE_OFFSET;
import static com.android.tools.r8.dex.Constants.TYPE_STRING_ID_ITEM;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.maindexlist.MainDexListTests;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.BitUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Adler32;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class DexContainerFormatTestBase extends TestBase {

  static void validateDex(Path output, int expectedDexes, DexVersion expectedVersion)
      throws Exception {
    List<byte[]> dexes = unzipContent(output);
    assertEquals(expectedDexes, dexes.size());
    for (byte[] dex : dexes) {
      validate(dex, expectedVersion);
    }
  }

  static void validateSingleContainerDex(Path output) throws Exception {
    List<byte[]> dexes = unzipContent(output);
    assertEquals(1, dexes.size());
    validate(dexes.get(0), DexVersion.V41);
  }

  static void validate(byte[] dex, DexVersion expectedVersion) throws Exception {
    CompatByteBuffer buffer = CompatByteBuffer.wrap(dex);
    setByteOrder(buffer);

    IntList sections = new IntArrayList();
    int offset = 0;
    while (offset < buffer.capacity()) {
      assertTrue(BitUtils.isAligned(4, offset));
      sections.add(offset);
      int dataSize = buffer.getInt(offset + DATA_SIZE_OFFSET);
      int dataOffset = buffer.getInt(offset + DATA_OFF_OFFSET);
      int file_size = buffer.getInt(offset + FILE_SIZE_OFFSET);
      if (expectedVersion.isContainerDex()) {
        assertEquals(0, dataSize);
        assertEquals(0, dataOffset);
      } else {
        assertEquals(file_size, dataOffset + dataSize);
      }
      offset += expectedVersion.isContainerDex() ? file_size : dataOffset + dataSize;
      assertEquals(file_size, offset - ListUtils.last(sections));
    }
    assertEquals(buffer.capacity(), offset);

    for (Integer sectionOffset : sections) {
      validateHeader(sections, buffer, sectionOffset, expectedVersion);
      validateMap(buffer, sectionOffset);
      validateSignature(buffer, sectionOffset);
      validateChecksum(buffer, sectionOffset);
    }
  }

  static byte[] magicBytes(DexVersion version) {
    byte[] magic = new byte[DEX_MAGIC_SIZE];
    System.arraycopy(
        Constants.DEX_FILE_MAGIC_PREFIX, 0, magic, 0, Constants.DEX_FILE_MAGIC_PREFIX.length);
    System.arraycopy(
        version.getBytes(),
        0,
        magic,
        Constants.DEX_FILE_MAGIC_PREFIX.length,
        version.getBytes().length);
    magic[Constants.DEX_FILE_MAGIC_PREFIX.length + version.getBytes().length] =
        Constants.DEX_FILE_MAGIC_SUFFIX;
    assertEquals(
        DEX_MAGIC_SIZE, Constants.DEX_FILE_MAGIC_PREFIX.length + version.getBytes().length + 1);
    return magic;
  }

  static void validateHeader(
      IntList sections, CompatByteBuffer buffer, int offset, DexVersion expectedVersion) {
    int lastOffset = sections.getInt(sections.size() - 1);
    int stringIdsSize = buffer.getInt(lastOffset + STRING_IDS_SIZE_OFFSET);
    int stringIdsOffset = buffer.getInt(lastOffset + STRING_IDS_OFF_OFFSET);

    byte[] magic = new byte[DEX_MAGIC_SIZE];
    buffer.get(magic);
    assertArrayEquals(magicBytes(expectedVersion), magic);

    assertEquals(
        expectedVersion.isContainerDex()
            ? Constants.TYPE_HEADER_ITEM_SIZE_V41
            : Constants.TYPE_HEADER_ITEM_SIZE,
        buffer.getInt(offset + HEADER_SIZE_OFFSET));
    assertEquals(stringIdsSize, buffer.getInt(offset + STRING_IDS_SIZE_OFFSET));
    assertEquals(stringIdsOffset, buffer.getInt(offset + STRING_IDS_OFF_OFFSET));
    if (expectedVersion.isContainerDex()) {
      assertEquals(0, buffer.getInt(offset + DATA_SIZE_OFFSET));
      assertEquals(0, buffer.getInt(offset + DATA_OFF_OFFSET));
      // Additional header field from V41.
      assertEquals(buffer.capacity(), buffer.getInt(offset + CONTAINER_SIZE_OFFSET));
      assertEquals(offset, buffer.getInt(offset + CONTAINER_OFF_OFFSET));
    }
    assertEquals(stringIdsSize, getSizeFromMap(TYPE_STRING_ID_ITEM, buffer, offset));
    assertEquals(stringIdsOffset, getOffsetFromMap(TYPE_STRING_ID_ITEM, buffer, offset));
  }

  static void validateMap(CompatByteBuffer buffer, int offset) {
    int mapOffset = buffer.getInt(offset + MAP_OFF_OFFSET);
    buffer.position(mapOffset);
    int mapSize = buffer.getInt();
    int previousOffset = Integer.MAX_VALUE;
    for (int i = 0; i < mapSize; i++) {
      buffer.getShort(); // Skip section type.
      buffer.getShort(); // Skip unused.
      buffer.getInt(); // Skip section size.
      int o = buffer.getInt();
      if (i > 0) {
        assertTrue("" + i + ": " + o + " " + previousOffset, o > previousOffset);
      }
      previousOffset = o;
    }
  }

  static void validateSignature(CompatByteBuffer buffer, int offset) throws Exception {
    int sectionSize = buffer.getInt(offset + FILE_SIZE_OFFSET);
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    md.update(
        buffer.asByteBuffer().array(), offset + FILE_SIZE_OFFSET, sectionSize - FILE_SIZE_OFFSET);
    byte[] expectedSignature = new byte[20];
    md.digest(expectedSignature, 0, 20);
    for (int i = 0; i < expectedSignature.length; i++) {
      assertEquals(expectedSignature[i], buffer.get(offset + SIGNATURE_OFFSET + i));
    }
  }

  static void validateChecksum(CompatByteBuffer buffer, int offset) {
    int sectionSize = buffer.getInt(offset + FILE_SIZE_OFFSET);
    Adler32 adler = new Adler32();
    adler.update(
        buffer.asByteBuffer().array(), offset + SIGNATURE_OFFSET, sectionSize - SIGNATURE_OFFSET);
    assertEquals((int) adler.getValue(), buffer.getInt(offset + CHECKSUM_OFFSET));
  }

  static int getSizeFromMap(int type, CompatByteBuffer buffer, int offset) {
    int mapOffset = buffer.getInt(offset + MAP_OFF_OFFSET);
    buffer.position(mapOffset);
    int mapSize = buffer.getInt();
    for (int i = 0; i < mapSize; i++) {
      int sectionType = buffer.getShort();
      buffer.getShort(); // Skip unused.
      int sectionSize = buffer.getInt();
      buffer.getInt(); // Skip offset.
      if (type == sectionType) {
        return sectionSize;
      }
    }
    throw new RuntimeException("Not found");
  }

  static int getOffsetFromMap(int type, CompatByteBuffer buffer, int offset) {
    int mapOffset = buffer.getInt(offset + MAP_OFF_OFFSET);
    buffer.position(mapOffset);
    int mapSize = buffer.getInt();
    for (int i = 0; i < mapSize; i++) {
      int sectionType = buffer.getShort();
      buffer.getShort(); // Skip unused.
      buffer.getInt(); // SKip size.
      int sectionOffset = buffer.getInt();
      if (type == sectionType) {
        return sectionOffset;
      }
    }
    throw new RuntimeException("Not found");
  }

  static void setByteOrder(CompatByteBuffer buffer) {
    // Make sure we set the right endian for reading.
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    int endian = buffer.getInt(Constants.ENDIAN_TAG_OFFSET);
    if (endian == Constants.REVERSE_ENDIAN_CONSTANT) {
      buffer.order(ByteOrder.BIG_ENDIAN);
    } else {
      if (endian != Constants.ENDIAN_CONSTANT) {
        throw new CompilationError("Unable to determine endianess for reading dex file.");
      }
    }
  }

  static List<byte[]> unzipContent(Path zip) throws IOException {
    List<byte[]> result = new ArrayList<>();
    ZipUtils.iter(zip, (entry, inputStream) -> result.add(ByteStreams.toByteArray(inputStream)));
    return result;
  }

  static void generateApplication(Path applicationJar, String rootPackage, int methodCount)
      throws Throwable {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < 10000; ++i) {
      String pkg = rootPackage + "." + (i % 2 == 0 ? "a" : "b");
      String className = "Class" + i;
      builder.add(pkg + "." + className);
    }
    List<String> classes = builder.build();

    generateApplication(applicationJar, classes, methodCount);
  }

  private static void generateApplication(Path output, List<String> classes, int methodCount)
      throws IOException {
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (String typename : classes) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(typename);
      byte[] bytes =
          transformer(MainDexListTests.ClassStub.class)
              .setClassDescriptor(descriptor)
              .addClassTransformer(
                  new ClassTransformer() {
                    @Override
                    public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                      // This strips <init>() too.
                      if (name.equals("methodStub")) {
                        for (int i = 0; i < methodCount; i++) {
                          MethodVisitor mv =
                              super.visitMethod(
                                  access, "method" + i, descriptor, signature, exceptions);
                          mv.visitCode();
                          mv.visitInsn(Opcodes.RETURN);
                          mv.visitMaxs(0, 0);
                          mv.visitEnd();
                        }
                      }
                      return null;
                    }
                  })
              .transform();
      consumer.accept(ByteDataView.of(bytes), descriptor, null);
    }
    consumer.finished(null);
  }

  // Simple stub/template for generating the input classes.
  public static class ClassStub {
    public static void methodStub() {}
  }
}
