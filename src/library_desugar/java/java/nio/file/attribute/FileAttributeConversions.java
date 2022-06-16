// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file.attribute;

import static java.util.ConversionRuntimeException.exception;

public class FileAttributeConversions {

  public static java.nio.file.attribute.FileTime convert(j$.nio.file.attribute.FileTime fileTime) {
    if (fileTime == null) {
      return null;
    }
    return java.nio.file.attribute.FileTime.fromMillis(fileTime.toMillis());
  }

  public static j$.nio.file.attribute.FileTime convert(java.nio.file.attribute.FileTime fileTime) {
    if (fileTime == null) {
      return null;
    }
    return j$.nio.file.attribute.FileTime.fromMillis(fileTime.toMillis());
  }

  public static java.nio.file.attribute.FileAttributeView convert(
      j$.nio.file.attribute.FileAttributeView fileAttributeView) {
    if (fileAttributeView == null) {
      return null;
    }
    if (fileAttributeView instanceof j$.nio.file.attribute.PosixFileAttributeView) {
      return j$.nio.file.attribute.PosixFileAttributeView.wrap_convert(
          (j$.nio.file.attribute.PosixFileAttributeView) fileAttributeView);
    }
    if (fileAttributeView instanceof j$.nio.file.attribute.FileOwnerAttributeView) {
      return j$.nio.file.attribute.FileOwnerAttributeView.wrap_convert(
          (j$.nio.file.attribute.FileOwnerAttributeView) fileAttributeView);
    }
    if (fileAttributeView instanceof j$.nio.file.attribute.BasicFileAttributeView) {
      return j$.nio.file.attribute.BasicFileAttributeView.wrap_convert(
          (j$.nio.file.attribute.BasicFileAttributeView) fileAttributeView);
    }
    throw exception("java.nio.file.attribute.FileAttributeView", fileAttributeView);
  }

  public static j$.nio.file.attribute.FileAttributeView convert(
      java.nio.file.attribute.FileAttributeView fileAttributeView) {
    if (fileAttributeView == null) {
      return null;
    }
    if (fileAttributeView instanceof java.nio.file.attribute.PosixFileAttributeView) {
      return j$.nio.file.attribute.PosixFileAttributeView.wrap_convert(
          (java.nio.file.attribute.PosixFileAttributeView) fileAttributeView);
    }
    if (fileAttributeView instanceof java.nio.file.attribute.FileOwnerAttributeView) {
      return j$.nio.file.attribute.FileOwnerAttributeView.wrap_convert(
          (java.nio.file.attribute.FileOwnerAttributeView) fileAttributeView);
    }
    if (fileAttributeView instanceof java.nio.file.attribute.BasicFileAttributeView) {
      return j$.nio.file.attribute.BasicFileAttributeView.wrap_convert(
          (java.nio.file.attribute.BasicFileAttributeView) fileAttributeView);
    }
    throw exception("java.nio.file.attribute.FileAttributeView", fileAttributeView);
  }

  public static java.nio.file.attribute.BasicFileAttributes convert(
      j$.nio.file.attribute.BasicFileAttributes fileAttributes) {
    if (fileAttributes == null) {
      return null;
    }
    if (fileAttributes instanceof j$.nio.file.attribute.PosixFileAttributes) {
      return j$.nio.file.attribute.PosixFileAttributes.wrap_convert(
          (j$.nio.file.attribute.PosixFileAttributes) fileAttributes);
    }
    return j$.nio.file.attribute.BasicFileAttributes.wrap_convert(fileAttributes);
  }

  public static j$.nio.file.attribute.BasicFileAttributes convert(
      java.nio.file.attribute.BasicFileAttributes fileAttributes) {
    if (fileAttributes == null) {
      return null;
    }
    if (fileAttributes instanceof java.nio.file.attribute.PosixFileAttributes) {
      return j$.nio.file.attribute.PosixFileAttributes.wrap_convert(
          (java.nio.file.attribute.PosixFileAttributes) fileAttributes);
    }
    return j$.nio.file.attribute.BasicFileAttributes.wrap_convert(fileAttributes);
  }
}
