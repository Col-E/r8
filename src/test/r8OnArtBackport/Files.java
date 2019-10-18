// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import com.android.tools.r8.MockedPath;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public final class Files {
  private Files() {}

  public static boolean exists(Path path, LinkOption... options) {
    if (options.length != 0) {
      throw new RuntimeException("Unsupported in the Files mock.");
    }
    return new File(path.toString()).exists();
  }

  public static boolean isDirectory(Path path, LinkOption... options) {
    if (options.length != 0) {
      throw new RuntimeException("Unsupported in the Files mock.");
    }
    return new File(path.toString()).isDirectory();
  }

  public static byte[] readAllBytes(Path path) throws IOException {
    if (!(path instanceof MockedPath)) {
      throw new RuntimeException("Unsupported in the Files mock.");
    }
    MockedPath mockedPath = (MockedPath) path;
    return mockedPath.getAllBytes();
  }

  public static OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    boolean append = false;
    for (OpenOption option : options) {
      if (option == StandardOpenOption.APPEND) {
        append = true;
      }
    }
    if (!(path instanceof MockedPath)) {
      throw new RuntimeException("Unsupported in the Files mock.");
    }
    MockedPath mockedPath = (MockedPath) path;
    return mockedPath.newOutputStream(append);
  }
}
