// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;

public class DesugarAndroidDefaultFileSystemProvider {
  private static final FileSystemProvider INSTANCE = DesugarAndroidFileSystemProvider.create();

  private DesugarAndroidDefaultFileSystemProvider() {}

  /** Returns the platform's default file system provider. */
  public static FileSystemProvider instance() {
    return INSTANCE;
  }

  /** Returns the platform's default file system. */
  public static FileSystem theFileSystem() {
    return INSTANCE.getFileSystem(URI.create("file:///"));
  }
}
