// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.adapter.AndroidVersionTest;
import java.io.IOException;
import java.nio.channels.DesugarChannels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/** Linux implementation of {@link FileSystemProvider} for desugar support. */
public class DesugarAndroidFileSystemProvider
    extends desugar.sun.nio.fs.DesugarLinuxFileSystemProvider {

  public static DesugarAndroidFileSystemProvider create() {
    return new DesugarAndroidFileSystemProvider(System.getProperty("user.dir"), "/");
  }

  DesugarAndroidFileSystemProvider(String userDir, String rootDir) {
    super(userDir, rootDir);
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    if (!containsCopyOption(options, StandardCopyOption.REPLACE_EXISTING) && Files.exists(target)) {
      throw new FileAlreadyExistsException(target.toString());
    }
    if (containsCopyOption(options, StandardCopyOption.ATOMIC_MOVE)) {
      throw new UnsupportedOperationException("Unsupported copy option");
    }
    super.copy(source, target, options);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    if (!containsCopyOption(options, StandardCopyOption.REPLACE_EXISTING) && Files.exists(target)) {
      throw new FileAlreadyExistsException(target.toString());
    }
    if (containsCopyOption(options, StandardCopyOption.COPY_ATTRIBUTES)) {
      throw new UnsupportedOperationException("Unsupported copy option");
    }
    super.move(source, target, options);
  }

  private boolean containsCopyOption(CopyOption[] options, CopyOption option) {
    for (CopyOption copyOption : options) {
      if (copyOption == option) {
        return true;
      }
    }
    return false;
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    if (path.toFile().isDirectory()) {
      throw new UnsupportedOperationException(
          "The desugar library does not support creating a file channel on a directory: " + path);
    }
    // A FileChannel is a SeekableByteChannel.
    return newFileChannel(path, options, attrs);
  }

  @Override
  public FileChannel newFileChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    if (AndroidVersionTest.is26OrAbove) {
      throw new RuntimeException("Above Api 26, the platform FileSystemProvider should be used.");
    }
    return DesugarChannels.openEmulatedFileChannel(path, options, attrs);
  }
}
