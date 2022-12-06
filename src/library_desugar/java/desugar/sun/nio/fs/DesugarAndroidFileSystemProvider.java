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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
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
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    if (dir.getParent() != null && !Files.exists(dir.getParent())) {
      throw new NoSuchFileException(dir.toString());
    }
    super.createDirectory(dir, attrs);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
      Path path, Class<V> type, LinkOption... options) {
    if (type == null) {
      throw new NullPointerException();
    }
    if (type == BasicFileAttributeView.class) {
      return type.cast(new DesugarAndroidBasicFileAttributeView(path));
    }
    return null;
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
      throws IOException {
    int attributesTypeIndexEnd = attributes.indexOf(":");
    final Class<? extends BasicFileAttributeView> attributeViewType;
    final String[] requestedAttributes;
    if (attributesTypeIndexEnd == -1) {
      attributeViewType = BasicFileAttributeView.class;
      requestedAttributes = attributes.split(",");
    } else {
      String attributeTypeSpec = attributes.substring(0, attributesTypeIndexEnd);
      if ("basic".equals(attributeTypeSpec)) {
        attributeViewType = BasicFileAttributeView.class;
      } else {
        throw new UnsupportedOperationException(
            String.format("Requested attribute type for: %s is not available.", attributeTypeSpec));
      }
      requestedAttributes = attributes.substring(attributesTypeIndexEnd + 1).split(",");
    }
    if (attributeViewType == BasicFileAttributeView.class) {
      DesugarBasicFileAttributeView attrView = new DesugarAndroidBasicFileAttributeView(path);
      return attrView.readAttributes(requestedAttributes);
    }
    throw new AssertionError("Unexpected View '" + attributeViewType + "' requested");
  }

  private boolean exists(Path file) {
    try {
      checkAccess(file);
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  @Override
  public void delete(Path path) throws IOException {
    if (exists(path)) {
      deleteIfExists(path);
      return;
    }
    throw new NoSuchFileException(path.toString());
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

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    // If the paths are equals, then it answers true even if they do not exist.
    if (path.equals(path2)) {
      return true;
    }
    // If the paths are not equal, they could still be equal due to symbolic link and so on, but
    // in that case accessibility is checked.
    checkAccess(path);
    checkAccess(path2);
    return super.isSameFile(path, path2);
  }
}
