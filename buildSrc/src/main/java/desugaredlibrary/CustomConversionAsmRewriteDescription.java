// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomConversionAsmRewriteDescription {

  static final String WRAP_CONVERT = "wrap_convert";
  static final String CONVERT = "convert";

  private static final Set<String> ENUM_WRAP_CONVERT_OWNER =
      ImmutableSet.of(
          "j$/nio/file/attribute/PosixFilePermission",
          "j$/util/stream/Collector$Characteristics");
  private static final Set<String> WRAP_CONVERT_OWNER =
      ImmutableSet.of(
          "j$/util/stream/DoubleStream",
          "j$/util/stream/IntStream",
          "j$/util/stream/LongStream",
          "j$/util/stream/Stream",
          "j$/nio/file/spi/FileSystemProvider",
          "j$/nio/file/spi/FileTypeDetector",
          "j$/nio/file/Path",
          "j$/nio/file/WatchEvent",
          "j$/nio/file/WatchEvent$Kind",
          "j$/nio/file/OpenOption",
          "j$/nio/file/attribute/FileAttribute");

  static Map<String, String> getWrapConvertOwnerMap() {
    HashMap<String, String> map = new HashMap<>();
    for (String theEnum : ENUM_WRAP_CONVERT_OWNER) {
      String theEnumJava = withJavaPrefix(theEnum);
      map.put(theEnum, theEnumJava + "$EnumConversion");
      map.put(theEnumJava, theEnumJava + "$EnumConversion");
    }
    for (String owner : WRAP_CONVERT_OWNER) {
      String ownerJava = withJavaPrefix(owner);
      map.put(ownerJava, ownerJava + "$Wrapper");
      map.put(owner, ownerJava + "$VivifiedWrapper");
    }
    return map;
  }

  private static String withJavaPrefix(String descriptor) {
    return "java" + descriptor.substring(2);
  }
}
