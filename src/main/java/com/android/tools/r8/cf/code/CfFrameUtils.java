// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.frame.FrameType;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;

public class CfFrameUtils {

  public static void storeLocal(
      int localIndex, FrameType frameType, Int2ObjectAVLTreeMap<FrameType> locals) {
    assert !frameType.isTwoWord();
    // Write low register.
    FrameType previousType = locals.put(localIndex, frameType);
    // Set low register -1 to top if it is the start of a wide primitive.
    if (previousType != null && previousType.isWidePrimitiveHigh()) {
      FrameType previousLowType = locals.put(localIndex - 1, FrameType.oneWord());
      assert previousLowType == previousType.asWidePrimitive().getLowType();
    }
    // Write high register.
    if (frameType.isWidePrimitive()) {
      assert frameType.isWidePrimitiveLow();
      previousType = locals.put(localIndex + 1, frameType.asWidePrimitive().getHighType());
    }
    // Set high register + 1 to top if it is the end of a wide primitive.
    if (previousType != null && previousType.isWidePrimitiveLow()) {
      FrameType previousHighType =
          locals.put(localIndex + frameType.getWidth(), FrameType.oneWord());
      assert previousHighType == previousType.asWidePrimitive().getHighType();
    }
  }

  public static boolean verifyLocals(Int2ObjectSortedMap<FrameType> locals) {
    for (Int2ObjectMap.Entry<FrameType> entry : locals.int2ObjectEntrySet()) {
      int localIndex = entry.getIntKey();
      FrameType frameType = entry.getValue();
      if (frameType.isWidePrimitiveLow()) {
        assert locals.get(localIndex + 1) == frameType.asWidePrimitive().getHighType();
      } else if (frameType.isWidePrimitiveHigh()) {
        assert locals.get(localIndex - 1) == frameType.asWidePrimitive().getLowType();
      } else {
        assert !frameType.isTwoWord();
      }
    }
    return true;
  }
}
