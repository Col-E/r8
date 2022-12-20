// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import java.util.List;

public class DexDebugUtils {

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(DexDebugInfo debugInfo) {
    return debugInfo == null
        || debugInfo.isPcBasedInfo()
        || verifySetPositionFramesFollowedByDefaultEvent(debugInfo.asEventBasedInfo().events);
  }

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(List<DexDebugEvent> events) {
    return verifySetPositionFramesFollowedByDefaultEvent(events.toArray(DexDebugEvent.EMPTY_ARRAY));
  }

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(DexDebugEvent... events) {
    for (int i = events.length - 1; i >= 0; i--) {
      if (events[i].isDefaultEvent()) {
        return true;
      }
      assert !events[i].isPositionFrame();
    }
    return true;
  }
}
