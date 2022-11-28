// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file.attribute;

import java.nio.file.FileApiFlips;
import java.util.Collections;
import java.util.Set;

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

  /** A FileAttribute usually holds an immutable set of non-null posix permissions. */
  public static java.nio.file.attribute.FileAttribute<?> convert(
      j$.nio.file.attribute.FileAttribute<?> attribute) {
    if (attribute == null) {
      return null;
    }
    if (isPosixPermissionAttributes(attribute.value())) {
      return new java.nio.file.attribute.FileAttribute<Object>() {
        public String name() {
          return "posix:permissions";
        }

        public Object value() {
          return Collections.unmodifiableSet(
              FileApiFlips.flipPosixPermissionSet((Set<?>) attribute.value()));
        }
      };
    }
    return j$.nio.file.attribute.FileAttributeWrapperMethods.wrap_convert(attribute);
  }

  public static j$.nio.file.attribute.FileAttribute<?> convert(
      java.nio.file.attribute.FileAttribute<?> attribute) {
    if (attribute == null) {
      return null;
    }
    if (isPosixPermissionAttributes(attribute.value())) {
      return new j$.nio.file.attribute.FileAttribute<Object>() {
        public String name() {
          return "posix:permissions";
        }

        public Object value() {
          return Collections.unmodifiableSet(
              FileApiFlips.flipPosixPermissionSet((Set<?>) attribute.value()));
        }
      };
    }
    return j$.nio.file.attribute.FileAttributeWrapperMethods.wrap_convert(attribute);
  }

  private static boolean isPosixPermissionAttributes(Object value) {
    if (value instanceof java.util.Set) {
      Set<?> set = (java.util.Set<?>) value;
      if (!set.isEmpty()) {
        Object guineaPig = set.iterator().next();
        if (guineaPig instanceof java.nio.file.attribute.PosixFilePermission
            || guineaPig instanceof j$.nio.file.attribute.PosixFilePermission) {
          return true;
        }
      }
    }
    return false;
  }
}
