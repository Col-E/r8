// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.Opcodes;

public class ConversionConverter {

  private static final Map<String, String> JAVA_WRAP_CONVERT_OWNER = new HashMap<>();
  private static final Map<String, String> J$_WRAP_CONVERT_OWNER = new HashMap<>();

  static {
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileSystemProvider",
        "java/nio/file/spi/FileSystemProvider$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileTypeDetector", "java/nio/file/spi/FileTypeDetector$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/StandardOpenOption", "java/nio/file/StandardOpenOption$EnumConversion");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/LinkOption", "java/nio/file/LinkOption$EnumConversion");
    JAVA_WRAP_CONVERT_OWNER.put("j$/nio/file/Path", "java/nio/file/Path$Wrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/WatchEvent", "java/nio/file/WatchEvent$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/BasicFileAttributes",
        "java/nio/file/attribute/BasicFileAttributes$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/BasicFileAttributeView",
        "java/nio/file/attribute/BasicFileAttributeView$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/FileOwnerAttributeView",
        "java/nio/file/attribute/FileOwnerAttributeView$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFileAttributes",
        "java/nio/file/attribute/PosixFileAttributes$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFileAttributeView",
        "java/nio/file/attribute/PosixFileAttributeView$VivifiedWrapper");
    JAVA_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFilePermission",
        "java/nio/file/attribute/PosixFilePermission$EnumConversion");

    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileSystemProvider", "java/nio/file/spi/FileSystemProvider$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileTypeDetector", "java/nio/file/spi/FileTypeDetector$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/StandardOpenOption", "java/nio/file/StandardOpenOption$EnumConversion");
    J$_WRAP_CONVERT_OWNER.put("j$/nio/file/LinkOption", "java/nio/file/LinkOption$EnumConversion");
    J$_WRAP_CONVERT_OWNER.put("j$/nio/file/Path", "java/nio/file/Path$VivifiedWrapper");
    J$_WRAP_CONVERT_OWNER.put("j$/nio/file/WatchEvent", "java/nio/file/WatchEvent$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/BasicFileAttributes",
        "java/nio/file/attribute/BasicFileAttributes$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/BasicFileAttributeView",
        "java/nio/file/attribute/BasicFileAttributeView$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/FileOwnerAttributeView",
        "java/nio/file/attribute/FileOwnerAttributeView$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFileAttributes",
        "java/nio/file/attribute/PosixFileAttributes$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFileAttributeView",
        "java/nio/file/attribute/PosixFileAttributeView$Wrapper");
    J$_WRAP_CONVERT_OWNER.put(
        "j$/nio/file/attribute/PosixFilePermission",
        "java/nio/file/attribute/PosixFilePermission$EnumConversion");
  }

  public static Path convertJar(Path jar) {
    String fileName = jar.getFileName().toString();
    String newFileName =
        fileName.substring(0, fileName.length() - ".jar".length()) + "_converted.jar";
    Path convertedJar = jar.getParent().resolve(newFileName);
    return internalConvert(jar, convertedJar);
  }

  private static synchronized Path internalConvert(Path jar, Path convertedJar) {
    if (Files.exists(convertedJar)) {
      return convertedJar;
    }

    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(
            new BufferedOutputStream(Files.newOutputStream(convertedJar, options)))) {
      new ConversionConverter().convert(jar, out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return convertedJar;
  }

  private void convert(Path desugaredLibraryFiles, ZipOutputStream out) throws IOException {
    ZipUtils.iter(
        desugaredLibraryFiles,
        ((entry, input) -> {
          if (!entry.getName().endsWith(".class")) {
            return;
          }
          final byte[] bytes = StreamUtils.streamToByteArrayClose(input);
          final byte[] rewrittenBytes =
              transformInvoke(entry.getName().substring(0, entry.getName().length() - 6), bytes);
          ZipUtils.writeToZipStream(out, entry.getName(), rewrittenBytes, ZipEntry.STORED);
        }));
  }

  private byte[] transformInvoke(String descriptor, byte[] bytes) {
    return ClassFileTransformer.create(bytes, Reference.classFromDescriptor(descriptor))
        .addMethodTransformer(getMethodTransformer())
        .transform();
  }

  private MethodTransformer getMethodTransformer() {
    return new MethodTransformer() {
      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == Opcodes.INVOKESTATIC && name.equals("wrap_convert")) {
          if (!JAVA_WRAP_CONVERT_OWNER.containsKey(owner)
              || !J$_WRAP_CONVERT_OWNER.containsKey(owner)) {
            throw new RuntimeException("Cannot transform wrap_convert method for " + owner);
          }
          if (owner.startsWith("java")) {
            String newOwner = J$_WRAP_CONVERT_OWNER.get(owner);
            super.visitMethodInsn(opcode, newOwner, "convert", descriptor, isInterface);
            return;
          } else if (owner.startsWith("j$")) {
            String newOwner = JAVA_WRAP_CONVERT_OWNER.get(owner);
            super.visitMethodInsn(opcode, newOwner, "convert", descriptor, isInterface);
            return;
          } else {
            throw new RuntimeException("Cannot transform wrap_convert method for " + owner);
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }
}
