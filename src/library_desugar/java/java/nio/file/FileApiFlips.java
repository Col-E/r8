// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import static java.util.ConversionRuntimeException.exception;

import java.nio.file.attribute.FileAttributeConversions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileApiFlips {

  public static Class<?> flipFileAttributes(Class<?> attributesClass) {
    if (attributesClass == null) {
      return null;
    }
    if (attributesClass == j$.nio.file.attribute.BasicFileAttributes.class) {
      return java.nio.file.attribute.BasicFileAttributes.class;
    }
    if (attributesClass == j$.nio.file.attribute.PosixFileAttributes.class) {
      return java.nio.file.attribute.PosixFileAttributes.class;
    }
    if (attributesClass == java.nio.file.attribute.BasicFileAttributes.class) {
      return j$.nio.file.attribute.BasicFileAttributes.class;
    }
    if (attributesClass == java.nio.file.attribute.PosixFileAttributes.class) {
      return j$.nio.file.attribute.PosixFileAttributes.class;
    }
    throw exception("java.nio.file.attribute.BasicFileAttributes", attributesClass);
  }

  public static Class<?> flipFileAttributeView(Class<?> attributeView) {
    if (attributeView == null) {
      return null;
    }
    if (attributeView == j$.nio.file.attribute.BasicFileAttributeView.class) {
      return java.nio.file.attribute.BasicFileAttributeView.class;
    }
    if (attributeView == j$.nio.file.attribute.PosixFileAttributeView.class) {
      return java.nio.file.attribute.PosixFileAttributeView.class;
    }
    if (attributeView == j$.nio.file.attribute.FileOwnerAttributeView.class) {
      return java.nio.file.attribute.FileOwnerAttributeView.class;
    }
    if (attributeView == java.nio.file.attribute.BasicFileAttributeView.class) {
      return j$.nio.file.attribute.BasicFileAttributeView.class;
    }
    if (attributeView == java.nio.file.attribute.PosixFileAttributeView.class) {
      return j$.nio.file.attribute.PosixFileAttributeView.class;
    }
    if (attributeView == java.nio.file.attribute.FileOwnerAttributeView.class) {
      return j$.nio.file.attribute.FileOwnerAttributeView.class;
    }
    throw exception("java.nio.file.attribute.FileAttributeView", attributeView);
  }

  public static RuntimeException exceptionOpenOption(Object suffix) {
    throw exception("java.nio.file.OpenOption", suffix);
  }

  public static Set<?> flipOpenOptionSet(Set<?> openOptionSet) {
    if (openOptionSet == null || openOptionSet.isEmpty()) {
      return openOptionSet;
    }
    HashSet<Object> convertedSet = new HashSet<>();
    Object guineaPig = openOptionSet.iterator().next();
    if (guineaPig instanceof java.nio.file.OpenOption) {
      for (Object item : openOptionSet) {
        java.nio.file.OpenOption option;
        try {
          option = (java.nio.file.OpenOption) item;
        } catch (ClassCastException cce) {
          throw exceptionOpenOption(cce);
        }
        convertedSet.add(j$.nio.file.OpenOption.wrap_convert(option));
      }
      return convertedSet;
    }
    if (guineaPig instanceof j$.nio.file.OpenOption) {
      for (Object item : openOptionSet) {
        j$.nio.file.OpenOption option;
        try {
          option = (j$.nio.file.OpenOption) item;
        } catch (ClassCastException cce) {
          throw exceptionOpenOption(cce);
        }
        convertedSet.add(j$.nio.file.OpenOption.wrap_convert(option));
      }
      return convertedSet;
    }
    throw exceptionOpenOption(guineaPig.getClass());
  }

  public static RuntimeException exceptionFileTime(Object suffix) {
    throw exception("java.nio.file.attribute.FileTime", suffix);
  }

  public static Object flipMaybeFileTime(Object val) {
    if (val instanceof j$.nio.file.attribute.FileTime) {
      j$.nio.file.attribute.FileTime fileTime;
      try {
        fileTime = (j$.nio.file.attribute.FileTime) val;
      } catch (ClassCastException cce) {
        throw exceptionFileTime(cce);
      }
      return FileAttributeConversions.convert(fileTime);
    }
    if (val instanceof java.nio.file.attribute.FileTime) {
      java.nio.file.attribute.FileTime fileTime;
      try {
        fileTime = (java.nio.file.attribute.FileTime) val;
      } catch (ClassCastException cce) {
        throw exceptionFileTime(cce);
      }
      return FileAttributeConversions.convert(fileTime);
    }
    return val;
  }

  public static Map<String, Object> flipMapWithMaybeFileTimeValues(Map<String, Object> in) {
    if (in == null || in.isEmpty()) {
      return in;
    }
    HashMap<String, Object> newMap = new HashMap<>();
    for (String key : in.keySet()) {
      newMap.put(key, flipMaybeFileTime(in.get(key)));
    }
    return newMap;
  }

  public static RuntimeException exceptionPosixPermission(Object suffix) {
    throw exception("java.nio.file.attribute.PosixFilePermission", suffix);
  }

  public static Set<?> flipPosixPermissionSet(Set<?> posixPermissions) {
    if (posixPermissions == null || posixPermissions.isEmpty()) {
      return posixPermissions;
    }
    HashSet<Object> convertedSet = new HashSet<>();
    Object guineaPig = posixPermissions.iterator().next();
    if (guineaPig instanceof java.nio.file.attribute.PosixFilePermission) {
      for (Object item : posixPermissions) {
        java.nio.file.attribute.PosixFilePermission permission;
        try {
          permission = (java.nio.file.attribute.PosixFilePermission) item;
        } catch (ClassCastException cce) {
          throw exceptionPosixPermission(cce);
        }
        convertedSet.add(j$.nio.file.attribute.PosixFilePermission.wrap_convert(permission));
      }
      return convertedSet;
    }
    if (guineaPig instanceof j$.nio.file.attribute.PosixFilePermission) {
      for (Object item : posixPermissions) {
        j$.nio.file.attribute.PosixFilePermission permission;
        try {
          permission = (j$.nio.file.attribute.PosixFilePermission) item;
        } catch (ClassCastException cce) {
          throw exceptionPosixPermission(cce);
        }
        convertedSet.add(j$.nio.file.attribute.PosixFilePermission.wrap_convert(permission));
      }
      return convertedSet;
    }
    throw exceptionPosixPermission(guineaPig.getClass());
  }

  public static RuntimeException exceptionWatchEvent(Object suffix) {
    throw exception("java.nio.file.WatchEvent", suffix);
  }

  public static List<?> flipWatchEventList(List<?> watchEventList) {
    if (watchEventList == null || watchEventList.isEmpty()) {
      return watchEventList;
    }
    List<Object> convertedList = new ArrayList<>();
    Object guineaPig = watchEventList.get(0);
    if (guineaPig instanceof java.nio.file.WatchEvent) {
      for (Object item : watchEventList) {
        java.nio.file.WatchEvent<?> watchEvent;
        try {
          watchEvent = (java.nio.file.WatchEvent<?>) item;
        } catch (ClassCastException cce) {
          throw exceptionWatchEvent(cce);
        }
        convertedList.add(j$.nio.file.WatchEvent.wrap_convert(watchEvent));
      }
      return convertedList;
    }
    if (guineaPig instanceof j$.nio.file.WatchEvent) {
      for (Object item : watchEventList) {
        j$.nio.file.WatchEvent<?> watchEvent;
        try {
          watchEvent = (j$.nio.file.WatchEvent<?>) item;
        } catch (ClassCastException cce) {
          throw exceptionWatchEvent(cce);
        }
        convertedList.add(j$.nio.file.WatchEvent.wrap_convert(watchEvent));
      }
      return convertedList;
    }
    throw exceptionWatchEvent(guineaPig.getClass());
  }
}
