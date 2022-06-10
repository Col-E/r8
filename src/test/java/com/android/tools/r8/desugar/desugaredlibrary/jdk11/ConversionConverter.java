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

  private static final Map<String, String> WRAP_CONVERT_OWNER = new HashMap<>();

  static {
    WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileSystemProvider",
        "java/nio/file/spi/FileSystemProvider$VivifiedWrapper");
    WRAP_CONVERT_OWNER.put(
        "j$/nio/file/spi/FileTypeDetector", "java/nio/file/spi/FileTypeDetector$VivifiedWrapper");
    WRAP_CONVERT_OWNER.put(
        "j$/nio/file/StandardOpenOption", "java/nio/file/StandardOpenOption$EnumConversion");
    WRAP_CONVERT_OWNER.put(
        "j$/nio/file/LinkOpenOption", "java/nio/file/LinkOpenOption$EnumConversion");
    WRAP_CONVERT_OWNER.put("j$/nio/file/Path", "java/nio/file/Path$Wrapper");
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
          if (WRAP_CONVERT_OWNER.containsKey(owner)) {
            String newOwner = WRAP_CONVERT_OWNER.get(owner);
            super.visitMethodInsn(opcode, newOwner, "convert", descriptor, isInterface);
            return;
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }
}
