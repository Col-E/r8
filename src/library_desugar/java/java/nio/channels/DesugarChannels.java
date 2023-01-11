// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.channels;

import desugar.sun.nio.fs.DesugarFileChannel;
import java.adapter.AndroidVersionTest;
import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DesugarChannels {

  /**
   * Below Api 24 FileChannel does not implement SeekableByteChannel. When we get one from the
   * library, we wrap it to implement the interface.
   */
  public static FileChannel convertMaybeLegacyFileChannelFromLibrary(FileChannel raw) {
    if (raw == null) {
      return null;
    }
    if (AndroidVersionTest.is24OrAbove) {
      return raw;
    }
    return DesugarFileChannel.wrap(raw);
  }

  /** The 2 open methods are present to be retargeted from FileChannel#open. */
  public static FileChannel open(Path path, OpenOption... openOptions) throws IOException {
    Set<OpenOption> openOptionSet = new HashSet<>();
    Collections.addAll(openOptionSet, openOptions);
    return open(path, openOptionSet);
  }

  public static FileChannel open(
      Path path, Set<? extends OpenOption> openOptions, FileAttribute<?>... attrs)
      throws IOException {
    if (AndroidVersionTest.is26OrAbove) {
      // This uses the library version of the method, the call is not rewritten.
      return FileChannel.open(path, openOptions, attrs);
    }
    return DesugarFileChannel.openEmulatedFileChannel(path, openOptions, attrs);
  }

}
