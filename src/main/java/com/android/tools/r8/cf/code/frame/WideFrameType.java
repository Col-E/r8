// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfFrame.FrameType;

public interface WideFrameType {

  FrameType asFrameType();

  boolean isDouble();

  boolean isLong();

  boolean isTwoWord();

  default boolean lessThanOrEqualTo(WideFrameType frameType) {
    return join(frameType) == frameType;
  }

  WideFrameType join(WideFrameType frameType);
}
