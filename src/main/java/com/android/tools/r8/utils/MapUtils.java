// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.Map;

public class MapUtils {

  public static <T> void removeIdentityMappings(Map<T, T> map) {
    map.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
  }

  public static String toString(Map<?, ?> map) {
    return StringUtils.join(
        map.entrySet(), ",", BraceType.TUBORG, entry -> entry.getKey() + ":" + entry.getValue());
  }
}
