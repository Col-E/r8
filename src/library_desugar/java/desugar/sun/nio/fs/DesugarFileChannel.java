// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

public class DesugarFileChannel {

  public static FileChannel wrap(FileChannel raw) {
    return null;
  }

  public static FileChannel openEmulatedFileChannel(
      Path path, Set<? extends OpenOption> openOptions, FileAttribute<?>... attrs) {
    return null;
  }
}
