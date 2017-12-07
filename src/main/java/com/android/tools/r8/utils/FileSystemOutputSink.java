// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.OutputSink;
import java.io.IOException;
import java.nio.file.Path;

public abstract class FileSystemOutputSink implements OutputSink {

  private final InternalOptions options;

  protected FileSystemOutputSink(InternalOptions options) {
    this.options = options;
  }

  public static FileSystemOutputSink create(Path outputPath, InternalOptions options)
      throws IOException {
    if (FileUtils.isArchive(outputPath)) {
      return new ZipFileOutputSink(outputPath, options);
    } else {
      return new DirectoryOutputSink(outputPath, options);
    }
  }

  String getOutputFileName(int index) {
    assert !options.outputClassFiles;
    return index == 0 ? "classes.dex" : ("classes" + (index + 1) + FileUtils.DEX_EXTENSION);
  }

  String getOutputFileName(String classDescriptor, String extension) throws IOException {
    assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
    return DescriptorUtils.getClassBinaryNameFromDescriptor(classDescriptor) + extension;
  }

  protected OutputMode getOutputMode() {
    return options.outputMode;
  }
}
