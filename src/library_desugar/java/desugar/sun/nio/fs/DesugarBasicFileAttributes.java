// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugar.sun.nio.fs;

import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public class DesugarBasicFileAttributes implements BasicFileAttributes {

  static DesugarBasicFileAttributes create(File file) {
    return null;
  }

  @Override
  public FileTime lastModifiedTime() {
    return null;
  }

  @Override
  public FileTime lastAccessTime() {
    return null;
  }

  @Override
  public FileTime creationTime() {
    return null;
  }

  @Override
  public boolean isRegularFile() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isSymbolicLink() {
    return false;
  }

  @Override
  public boolean isOther() {
    return false;
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public Object fileKey() {
    return null;
  }
}
