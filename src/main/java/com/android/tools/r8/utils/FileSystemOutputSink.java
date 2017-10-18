// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.OutputSink;
import com.google.common.io.Closer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public abstract class FileSystemOutputSink implements OutputSink {

  private final InternalOptions options;

  protected FileSystemOutputSink(InternalOptions options) {
    this.options = options;
  }

  public static FileSystemOutputSink create(Path outputPath, InternalOptions options)
      throws IOException {
    if (FileUtils.isZipFile(outputPath)) {
      return new ZipFileOutputSink(outputPath, options);
    } else {
      return new DirectoryOutputSink(outputPath, options);
    }
  }

  protected Path getOutputFileName(int index) {
    String file = index == 0 ? "classes.dex" : ("classes" + (index + 1) + ".dex");
    return Paths.get(file);
  }

  protected Path getOutputFileName(String classDescriptor) throws IOException {
    assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
    Path result = Paths.get(classDescriptor.substring(1, classDescriptor.length() - 1) + ".dex");
    return result;
  }

  @Override
  public void writePrintUsedInformation(byte[] contents) throws IOException {
    writeToFile(options.proguardConfiguration.getPrintUsageFile(), System.out, contents);
  }

  @Override
  public void writeProguardMapFile(byte[] contents) throws IOException {
    writeToFile(options.proguardConfiguration.getPrintMappingFile(), System.out, contents);
  }

  @Override
  public void writeProguardSeedsFile(byte[] contents) throws IOException {
    writeToFile(options.proguardConfiguration.getSeedFile(), System.out, contents);
  }

  @Override
  public void writeMainDexListFile(byte[] contents) throws IOException {
    writeToFile(options.printMainDexListFile, System.out, contents);
  }

  protected void writeToFile(Path output, OutputStream defValue, byte[] contents)
      throws IOException {
    Closer closer = Closer.create();
    OutputStream outputStream =
        FileUtils.openPathWithDefault(
            closer,
            output,
            defValue,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    outputStream.write(contents);
  }

  protected OutputMode getOutputMode() {
    return options.outputMode;
  }
}
