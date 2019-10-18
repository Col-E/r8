// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;

// Removed Watchable.
public interface Path extends Comparable<Path>, Iterable<Path> {
  public static Path of(String first, String... more) {
    throw new RuntimeException("Path does not implement of, use MockedPath.of");
  }

  public static Path of(URI uri) {
    throw new RuntimeException("Path does not implement of, use MockedPath.of");
  }

  FileSystem getFileSystem();

  boolean isAbsolute();

  Path getRoot();

  Path getFileName();

  Path getParent();

  int getNameCount();

  Path getName(int index);

  Path subpath(int beginIndex, int endIndex);

  boolean startsWith(Path other);

  default boolean startsWith(String other) {
    throw new RuntimeException("Path does not implement startsWith");
  }

  boolean endsWith(Path other);

  default boolean endsWith(String other) {
    throw new RuntimeException("Path does not implement endsWith");
  }

  Path normalize();

  Path resolve(Path other);

  default Path resolve(String other) {
    throw new RuntimeException("Path does not implement resolve");
  }

  default Path resolveSibling(Path other) {
    throw new RuntimeException("Path does not implement resolveSibling");
  }

  default Path resolveSibling(String other) {
    throw new RuntimeException("Path does not implement resolveSibling");
  }

  Path relativize(Path other);

  URI toUri();

  Path toAbsolutePath();

  Path toRealPath(LinkOption... options) throws IOException;

  default File toFile() {
    throw new RuntimeException("Path does not implement toFile");
  }

  @Override
  default Iterator<Path> iterator() {
    throw new RuntimeException("Path does not implement iterator");
  }

  @Override
  int compareTo(Path other);

  boolean equals(Object other);

  int hashCode();

  String toString();
}
