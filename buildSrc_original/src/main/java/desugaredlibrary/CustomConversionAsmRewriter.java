// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import static desugaredlibrary.AsmRewriter.ASM_VERSION;
import static desugaredlibrary.CustomConversionAsmRewriteDescription.CONVERT;
import static desugaredlibrary.CustomConversionAsmRewriteDescription.WRAP_CONVERT;
import static desugaredlibrary.CustomConversionAsmRewriter.CustomConversionVersion.LEGACY;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.google.common.io.ByteStreams;
import desugaredlibrary.AsmRewriter.MethodTransformer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CustomConversionAsmRewriter {

  public enum CustomConversionVersion {
    LEGACY,
    LATEST
  }

  public CustomConversionAsmRewriter(CustomConversionVersion legacy) {
    this.legacy = legacy;
  }

  private final CustomConversionVersion legacy;
  private final Map<String, String> wrapConvertOwnerMap =
      CustomConversionAsmRewriteDescription.getWrapConvertOwnerMap();

  public static void generateJars(Path jar, Path outputDirectory) throws IOException {
    for (CustomConversionVersion version : CustomConversionVersion.values()) {
      new CustomConversionAsmRewriter(version).convert(jar, outputDirectory);
    }
  }

  private void convert(Path jar, Path outputDirectory) throws IOException {
    String fileName = jar.getFileName().toString();
    String newFileName =
        fileName.substring(0, fileName.length() - "_raw.jar".length())
            + (legacy == LEGACY ? "_legacy" : "")
            + ".jar";
    Path convertedJar = outputDirectory.resolve(newFileName);
    internalConvert(jar, convertedJar);
  }

  private void internalConvert(Path jar, Path convertedJar) throws IOException {
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(
            new BufferedOutputStream(Files.newOutputStream(convertedJar, options)))) {
      new CustomConversionAsmRewriter(legacy).convert(jar, out, legacy);
    }
  }

  private void convert(
      Path desugaredLibraryFiles, ZipOutputStream out, CustomConversionVersion legacy)
      throws IOException {
    try (ZipFile zipFile = new ZipFile(desugaredLibraryFiles.toFile(), StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream entryStream = zipFile.getInputStream(entry)) {
          handleFile(entry, entryStream, out, legacy);
        }
      }
    }
  }

  private void handleFile(
      ZipEntry entry, InputStream input, ZipOutputStream out, CustomConversionVersion legacy)
      throws IOException {
    if (!entry.getName().endsWith(".class")) {
      return;
    }
    if (legacy == LEGACY
        && (entry.getName().contains("java/nio/file")
            || entry.getName().contains("ApiFlips")
            || entry.getName().contains("java/adapter"))) {
      return;
    }
    final byte[] bytes = ByteStreams.toByteArray(input);
    input.close();
    final byte[] rewrittenBytes = transformInvoke(bytes);
    writeToZipStream(out, entry.getName(), rewrittenBytes, ZipEntry.STORED);
  }

  public static void writeToZipStream(
      ZipOutputStream stream, String entry, byte[] content, int compressionMethod)
      throws IOException {
    int offset = 0;
    int length = content.length;
    CRC32 crc = new CRC32();
    crc.update(content, offset, length);
    ZipEntry zipEntry = new ZipEntry(entry);
    zipEntry.setMethod(compressionMethod);
    zipEntry.setSize(length);
    zipEntry.setCrc(crc.getValue());
    zipEntry.setTime(0);
    stream.putNextEntry(zipEntry);
    stream.write(content, offset, length);
    stream.closeEntry();
  }

  private byte[] transformInvoke(byte[] bytes) {
    return AsmRewriter.transformInvoke(bytes, new CustomConversionRewriter(ASM_VERSION));
  }

  class CustomConversionRewriter extends MethodTransformer {

    protected CustomConversionRewriter(int api) {
      super(api);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (opcode == INVOKESTATIC && name.equals(WRAP_CONVERT)) {
        convertInvoke(opcode, owner, descriptor, isInterface);
        return;
      }
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    private String extractFirstArg(String descriptor) {
      assert descriptor.startsWith("(L");
      int end = descriptor.indexOf(';');
      assert end > 2;
      return descriptor.substring(2, end);
    }

    private void convertInvoke(int opcode, String owner, String descriptor, boolean isInterface) {
      String firstArg = extractFirstArg(descriptor);
      if (!wrapConvertOwnerMap.containsKey(firstArg)
          || !(firstArg.startsWith("java") || firstArg.startsWith("j$"))) {
        throw new RuntimeException(
            "Cannot transform wrap_convert method for " + firstArg + " (owner: " + owner + ")");
      }
      String newOwner = wrapConvertOwnerMap.get(firstArg);
      super.visitMethodInsn(opcode, newOwner, CONVERT, descriptor, isInterface);
    }

  }
}
