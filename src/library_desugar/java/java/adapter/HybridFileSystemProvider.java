// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.adapter;

import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import desugar.sun.nio.fs.DesugarAndroidDefaultFileSystemProvider;
import j$.nio.file.FileSystems;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;

/**
 * A hybrid file system provider adapter that delegates different implementations based on the
 * runtime environment.
 */
public final class HybridFileSystemProvider {

  private static final FileSystemProvider INSTANCE = getFileSystemProvider();
  private static final FileSystem FILE_SYSTEM_INSTANCE =
      INSTANCE.getFileSystem(URI.create("file:///"));

  private static FileSystemProvider getFileSystemProvider() {
    if (AndroidVersionTest.is26OrAbove) {
      // On API 26 and above, FileSystems is present.
      j$.nio.file.FileSystem fileSystem = FileSystems.getDefault();
      j$.nio.file.spi.FileSystemProvider provider = fileSystem.provider();
      return j$.nio.file.spi.FileSystemProvider.wrap_convert(provider);
    }
    if (AndroidVersionTest.isHeadfull) {
      // The DesugarDefaultFileSystemProvider requires the ThreadPolicy to be set to work correctly.
      // We cannot set the ThreadPolicy in headless and it should not matter.
      setThreadPolicy();
    }
    return DesugarAndroidDefaultFileSystemProvider.instance();
  }

  private static void setThreadPolicy() {
    // The references to the android.os methods need to be outlined.
    // TODO(b/207004118): Fix the strict mode allowlisting.
    ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
    StrictMode.setThreadPolicy(new ThreadPolicy.Builder(threadPolicy).permitDiskReads().build());
  }

  private HybridFileSystemProvider() {}

  /** Returns the platform's default file system provider. */
  public static FileSystemProvider instance() {
    return INSTANCE;
  }

  /** Returns the platform's default file system. */
  public static FileSystem theFileSystem() {
    return FILE_SYSTEM_INSTANCE;
  }
}
