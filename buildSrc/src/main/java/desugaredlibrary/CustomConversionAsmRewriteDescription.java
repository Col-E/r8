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
  static final String INVERTED_WRAP_CONVERT = "inverted_wrap_convert";
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
          "j$/nio/file/OpenOption");

  static Map<String, String> getJavaWrapConvertOwnerMap() {
    return computeConvertOwnerMap("$Wrapper");
  }

  static Map<String, String> getJ$WrapConvertOwnerMap() {
    return computeConvertOwnerMap("$VivifiedWrapper");
  }

  private static HashMap<String, String> computeConvertOwnerMap(String suffix) {
    HashMap<String, String> map = new HashMap<>();
    for (String theEnum : ENUM_WRAP_CONVERT_OWNER) {
      map.put(theEnum, theEnum + "$EnumConversion");
    }
    for (String owner : WRAP_CONVERT_OWNER) {
      map.put(owner, owner + suffix);
    }
    return map;
  }
}
