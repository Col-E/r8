// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

public class DesugarLinuxFileSystemProvider extends FileSystemProvider {

  DesugarLinuxFileSystemProvider(String userDir, String rootDir) {
    super();
  }

  @Override
  public String getScheme() {
    return null;
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> map) throws IOException {
    return null;
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    return null;
  }

  @Override
  public Path getPath(URI uri) {
    return null;
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> set, FileAttribute<?>... fileAttributes)
      throws IOException {
    return null;
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path path, Filter<? super Path> filter)
      throws IOException {
    return null;
  }

  @Override
  public void createDirectory(Path path, FileAttribute<?>... fileAttributes) throws IOException {}

  @Override
  public void delete(Path path) throws IOException {}

  @Override
  public void copy(Path path, Path path1, CopyOption... copyOptions) throws IOException {}

  @Override
  public void move(Path path, Path path1, CopyOption... copyOptions) throws IOException {}

  @Override
  public boolean isSameFile(Path path, Path path1) throws IOException {
    return false;
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return false;
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    return null;
  }

  @Override
  public void checkAccess(Path path, AccessMode... accessModes) throws IOException {}

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
      Path path, Class<V> aClass, LinkOption... linkOptions) {
    return null;
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(
      Path path, Class<A> aClass, LinkOption... linkOptions) throws IOException {
    return null;
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String s, LinkOption... linkOptions)
      throws IOException {
    return null;
  }

  @Override
  public void setAttribute(Path path, String s, Object o, LinkOption... linkOptions)
      throws IOException {}
}
