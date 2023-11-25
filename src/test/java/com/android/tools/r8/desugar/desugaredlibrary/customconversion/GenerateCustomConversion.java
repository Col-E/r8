// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.customconversion;

import static com.android.tools.r8.TestBase.transformer;
import static com.android.tools.r8.desugar.desugaredlibrary.customconversion.CustomConversionAsmRewriteDescription.CONVERT;
import static com.android.tools.r8.desugar.desugaredlibrary.customconversion.CustomConversionAsmRewriteDescription.WRAP_CONVERT;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion;
import com.android.tools.r8.transformers.MethodTransformer;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class GenerateCustomConversion {

  public GenerateCustomConversion(CustomConversionVersion version) {
    this.version = version;
  }

  private final CustomConversionVersion version;
  private final Map<String, String> wrapConvertOwnerMap =
      CustomConversionAsmRewriteDescription.getWrapConvertOwnerMap();

  public static Collection<Path> generateJars(Path jar, Path outputDirectory) throws IOException {
    List<Path> generatedJars = new ArrayList<>();
    for (CustomConversionVersion version : CustomConversionVersion.values()) {
      generatedJars.add(new GenerateCustomConversion(version).convert(jar, outputDirectory));
    }
    return generatedJars;
  }

  private Path convert(Path jar, Path outputDirectory) throws IOException {
    Path convertedJar = outputDirectory.resolve(version.getFileName());
    internalConvert(jar, convertedJar);
    assert Files.exists(convertedJar) : "Custom conversion generation did not generate anything.";
    return convertedJar;
  }

  private void internalConvert(Path jar, Path convertedJar) throws IOException {
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(
            new BufferedOutputStream(Files.newOutputStream(convertedJar, options)))) {
      new GenerateCustomConversion(version).convert(jar, out);
    }
  }

  private void convert(Path desugaredLibraryFiles, ZipOutputStream out) throws IOException {
    boolean fileGotWritten = false;
    try (ZipFile zipFile = new ZipFile(desugaredLibraryFiles.toFile(), StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream entryStream = zipFile.getInputStream(entry)) {
          fileGotWritten |= handleFile(entry, entryStream, out);
        }
      }
    }
    assert fileGotWritten : "No files were written when converting custom conversions.";
  }

  private boolean handleFile(ZipEntry entry, InputStream input, ZipOutputStream out)
      throws IOException {
    if (!entry.getName().endsWith(".class")) {
      return false;
    }
    if (version == CustomConversionVersion.LEGACY
        && (entry.getName().contains("java/nio/file")
            || entry.getName().contains("ApiFlips")
            || entry.getName().contains("java/adapter"))) {
      return false;
    }
    final byte[] bytes = ByteStreams.toByteArray(input);
    input.close();
    final byte[] rewrittenBytes = transformInvoke(bytes);
    writeToZipStream(out, entry.getName(), rewrittenBytes, ZipEntry.STORED);
    return true;
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
    return transformer(bytes, null)
        .addMethodTransformer(new CustomConversionMethodTransformer())
        .transform();
  }

  class CustomConversionMethodTransformer extends MethodTransformer {

    protected CustomConversionMethodTransformer() {
      super();
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
