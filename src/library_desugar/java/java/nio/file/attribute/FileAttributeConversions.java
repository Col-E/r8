// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file.attribute;

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

}
