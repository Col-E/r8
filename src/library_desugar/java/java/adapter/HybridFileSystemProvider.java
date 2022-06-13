// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.adapter;

import android.os.Build.VERSION;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import desugar.sun.nio.fs.DesugarDefaultFileSystemProvider;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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
    if (VERSION.SDK_INT >= 26) {
      return FileSystems.getDefault().provider();
    } else {
      try {
        // In headless, android.os is absent so the following line will throw.
        // We cannot set the ThreadPolicy in headless and it is irrelevant.
        // If we are not in headless, the class will be found and we can set the thread policy.
        Class.forName("android.os.Build");
        setThreadPolicy();
      } catch (ClassNotFoundException ignored) {
        // Headless mode.
      }
      return DesugarDefaultFileSystemProvider.instance();
    }
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
