// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.adapter;

import desugar.sun.nio.fs.DesugarDefaultFileTypeDetector;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

/**
 * A hybrid file type detector adapter that delegates different implementations based on the runtime
 * environment.
 */
public final class HybridFileTypeDetector {
  private HybridFileTypeDetector() {}

  public static FileTypeDetector create() {
    return AndroidVersionTest.is26OrAbove
        ? new PlatformFileTypeDetector()
        : DesugarDefaultFileTypeDetector.create();
  }

  static class PlatformFileTypeDetector extends FileTypeDetector {
    @Override
    public String probeContentType(Path path) throws IOException {
      return j$.nio.file.Files.probeContentType(j$.nio.file.Path.wrap_convert(path));
    }
  }
}
