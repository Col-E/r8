// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;

/** Creates this platform's default FileSystemProvider. */
public class DesugarDefaultFileSystemProvider {

  /** Returns the platform's default file system provider. */
  public static FileSystemProvider instance() {
    return null;
  }

  /** Returns the platform's default file system. */
  public static FileSystem theFileSystem() {
    return null;
  }
}
