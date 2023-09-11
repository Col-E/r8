// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.DEX_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.MODULE_INFO_CLASS;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.references.ClassReference;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

  // Beginning of extra field length: https://en.wikipedia.org/wiki/ZIP_(file_format)
  private static final int EXTRA_FIELD_LENGTH_OFFSET = 30;

  public static void writeResourcesToZip(
      List<ProgramResource> resources,
      Set<DataDirectoryResource> dataDirectoryResources,
      Set<DataEntryResource> dataEntryResources,
      Closer closer,
      ZipOutputStream out)
      throws IOException, ResourceException {
    for (DataDirectoryResource dataDirectoryResource : dataDirectoryResources) {
      writeToZipStream(out, dataDirectoryResource.getName(), new byte[0], ZipEntry.DEFLATED);
    }
    for (DataEntryResource dataEntryResource : dataEntryResources) {
      String entryName = dataEntryResource.getName();
      byte[] bytes = ByteStreams.toByteArray(closer.register(dataEntryResource.getByteStream()));
      writeToZipStream(
          out,
          entryName,
          bytes,
          AndroidApiDataAccess.isApiDatabaseEntry(entryName) ? ZipEntry.STORED : ZipEntry.DEFLATED);
    }
    for (ProgramResource resource : resources) {
      assert resource.getClassDescriptors().size() == 1;
      Iterator<String> iterator = resource.getClassDescriptors().iterator();
      String className = iterator.next();
      String entryName = DescriptorUtils.getClassFileName(className);
      byte[] bytes = ByteStreams.toByteArray(closer.register(resource.getByteStream()));
      writeToZipStream(out, entryName, bytes, ZipEntry.DEFLATED);
    }
  }

  public interface OnEntryHandler {
    void onEntry(ZipEntry entry, InputStream input) throws IOException;
  }

  public static void iter(String zipFileStr, OnEntryHandler handler) throws IOException {
    iter(Paths.get(zipFileStr), handler);
  }

  public static void iter(Path zipFilePath, OnEntryHandler handler) throws IOException {
    try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        try (InputStream entryStream = zipFile.getInputStream(entry)) {
          handler.onEntry(entry, entryStream);
        }
      }
    }
  }

  @SuppressWarnings("UnnecessaryParentheses")
  public static boolean containsEntry(Path zipfile, String name) throws IOException {
    BooleanBox result = new BooleanBox();
    ZipUtils.iter(
        zipfile,
        (entry, stream) -> {
          result.computeIfNotSet(() -> entry.getName().equals(name));
        });
    return result.get();
  }

  @SuppressWarnings("UnnecessaryParentheses")
  public static Path map(
      Path zipFilePath, Path mappedFilePath, BiFunction<ZipEntry, byte[], byte[]> map)
      throws IOException {
    ZipBuilder builder = ZipBuilder.builder(mappedFilePath);
    ZipUtils.iter(
        zipFilePath,
        ((entry, input) -> {
          builder.addBytes(entry.getName(), map.apply(entry, ByteStreams.toByteArray(input)));
        }));
    return builder.build();
  }

  @SuppressWarnings("UnnecessaryParentheses")
  public static Path filter(Path zipFilePath, Path filteredFilePath, Predicate<ZipEntry> predicate)
      throws IOException {
    ZipBuilder builder = ZipBuilder.builder(filteredFilePath);
    ZipUtils.iter(
        zipFilePath,
        ((entry, input) -> {
          if (predicate.test(entry)) {
            builder.addBytes(entry.getName(), ByteStreams.toByteArray(input));
          }
        }));
    return builder.build();
  }

  public static byte[] readSingleEntry(Path zipFilePath, String name) throws IOException {
    try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), StandardCharsets.UTF_8)) {
      return ByteStreams.toByteArray(zipFile.getInputStream(zipFile.getEntry(name)));
    }
  }

  @SuppressWarnings("StreamResourceLeak")
  public static void zip(Path zipFile, Path inputDirectory) throws IOException {
    List<Path> files =
        Files.walk(inputDirectory)
            .filter(path -> !Files.isDirectory(path))
            .collect(Collectors.toList());
    zip(zipFile, inputDirectory, files);
  }

  public static void zip(Path zipFile, Path basePath, Collection<Path> filesToZip)
      throws IOException {
    try (ZipOutputStream stream =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
      zip(stream, basePath, filesToZip);
    }
  }

  public static void zip(ZipOutputStream stream, Path basePath, Collection<Path> filesToZip)
      throws IOException {
    for (Path path : filesToZip) {
      ZipEntry zipEntry =
          new ZipEntry(
              StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(
                          basePath.relativize(path).iterator(), Spliterator.ORDERED),
                      false)
                  .map(Path::toString)
                  .collect(Collectors.joining("/")));
      stream.putNextEntry(zipEntry);
      Files.copy(path, stream);
      stream.closeEntry();
    }
  }

  public static void zip(Path zipFile, Path basePath, Path... filesToZip) throws IOException {
    zip(zipFile, basePath, Arrays.asList(filesToZip));
  }

  public static List<Path> unzip(Path zipFile, Path outDirectory) throws IOException {
    return unzip(zipFile, outDirectory, (entry) -> true, Function.identity());
  }

  public static List<File> unzip(String zipFile, File outDirectory) throws IOException {
    return unzip(Paths.get(zipFile), outDirectory.toPath(), (entry) -> true, Path::toFile);
  }

  public static List<Path> unzip(Path zipFile, Path outDirectory, Predicate<ZipEntry> filter)
      throws IOException {
    return unzip(zipFile, outDirectory, filter, Function.identity());
  }

  public static <T> List<T> unzip(
      Path zipFile, Path outDirectory, Predicate<ZipEntry> filter, Function<Path, T> map)
      throws IOException {
    final List<T> outFiles = new ArrayList<>();
    iter(
        zipFile,
        (entry, input) -> {
          String name = entry.getName();
          if (!entry.isDirectory() && filter.test(entry)) {
            if (name.contains("..")) {
              // Protect against malicious archives.
              throw new CompilationError("Invalid entry name \"" + name + "\"");
            }
            Path outPath = outDirectory.resolve(name);
            outPath.toFile().getParentFile().mkdirs();
            try (OutputStream output = new FileOutputStream(outPath.toFile())) {
              ByteStreams.copy(input, output);
            }
            outFiles.add(map.apply(outPath));
          }
        });
    return outFiles;
  }

  public static void writeToZipStream(
      ZipOutputStream stream, String entry, byte[] content, int compressionMethod)
      throws IOException {
    writeToZipStream(stream, entry, ByteDataView.of(content), compressionMethod);
  }

  public static void writeToZipStream(
      ZipOutputStream stream, String entry, ByteDataView content, int compressionMethod)
      throws IOException {
    byte[] buffer = content.getBuffer();
    int offset = content.getOffset();
    int length = content.getLength();
    CRC32 crc = new CRC32();
    crc.update(buffer, offset, length);
    ZipEntry zipEntry = new ZipEntry(entry);
    zipEntry.setMethod(compressionMethod);
    zipEntry.setSize(length);
    zipEntry.setCrc(crc.getValue());
    zipEntry.setTime(0);
    stream.putNextEntry(zipEntry);
    stream.write(buffer, offset, length);
    stream.closeEntry();
  }

  public static boolean isDexFile(String entry) {
    String name = StringUtils.toLowerCase(entry);
    return name.endsWith(DEX_EXTENSION);
  }

  public static boolean isClassFile(String entry) {
    if (entry.endsWith(MODULE_INFO_CLASS)) {
      return false;
    }
    // Only check for upper case META-INF. See JAR File Specification,
    // https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html.
    if (entry.startsWith("META-INF") || entry.startsWith("/META-INF")) {
      return false;
    }
    return entry.endsWith(CLASS_EXTENSION);
  }

  public static class ZipBuilder {
    private final Path zipFile;
    private final ZipOutputStream stream;

    private ZipBuilder(Path zipFile) throws IOException {
      this.zipFile = zipFile;
      stream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)));
    }

    public static ZipBuilder builder(Path zipFile) throws IOException {
      return new ZipBuilder(zipFile);
    }

    public ZipOutputStream getOutputStream() {
      return stream;
    }

    public ZipBuilder addFile(String name, Path file) throws IOException {
      ZipEntry zipEntry = new ZipEntry(name);
      stream.putNextEntry(zipEntry);
      Files.copy(file, stream);
      stream.closeEntry();
      return this;
    }

    public ZipBuilder addFilesRelative(Path basePath, Collection<Path> filesToAdd)
        throws IOException {
      for (Path path : filesToAdd) {
        ZipEntry zipEntry =
            new ZipEntry(
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                            basePath.relativize(path).iterator(), Spliterator.ORDERED),
                        false)
                    .map(Path::toString)
                    .collect(Collectors.joining("/")));
        stream.putNextEntry(zipEntry);
        Files.copy(path, stream);
        stream.closeEntry();
      }
      return this;
    }

    public ZipBuilder addFilesRelative(Path basePath, Path... filesToAdd) throws IOException {
      return addFilesRelative(basePath, Arrays.asList(filesToAdd));
    }

    public ZipBuilder addBytes(String path, byte[] bytes) throws IOException {
      ZipEntry zipEntry = new ZipEntry(path);
      stream.putNextEntry(zipEntry);
      stream.write(bytes);
      stream.closeEntry();
      return this;
    }

    public ZipBuilder addText(String path, String text) throws IOException {
      ZipEntry zipEntry = new ZipEntry(path);
      stream.putNextEntry(zipEntry);
      stream.write(text.getBytes(StandardCharsets.UTF_8));
      stream.closeEntry();
      return this;
    }

    public Path build() throws IOException {
      stream.close();
      return zipFile;
    }
  }

  public static String zipEntryNameForClass(Class<?> clazz) {
    return DescriptorUtils.getClassBinaryName(clazz) + CLASS_EXTENSION;
  }

  public static String zipEntryNameForClass(ClassReference clazz) {
    return clazz.getBinaryName() + CLASS_EXTENSION;
  }

  public static long getOffsetOfResourceInZip(File file, String entry) throws IOException {
    // Look into the jar file to see find the offset.
    ZipFile zipFile = new ZipFile(file);
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    long offset = 0;
    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      byte[] extra = zipEntry.getExtra();
      offset +=
          EXTRA_FIELD_LENGTH_OFFSET
              + zipEntry.getName().length()
              + (extra == null ? 0 : extra.length);
      if (zipEntry.getName().equals(entry)) {
        return zipEntry.getSize() == zipEntry.getCompressedSize() ? offset : -1;
      } else if (!zipEntry.isDirectory()) {
        offset += zipEntry.getCompressedSize();
      }
    }
    return -1;
  }
}
