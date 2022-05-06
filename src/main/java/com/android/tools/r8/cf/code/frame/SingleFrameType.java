// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfFrame.SingleInitializedType;

public interface SingleFrameType {

  FrameType asFrameType();

  boolean isInitialized();

  SingleInitializedType asSingleInitializedType();

  boolean isNullType();

  boolean isOneWord();

  boolean isTop();

  boolean isUninitializedObject();

  boolean isUninitializedThis();

  SingleFrameType join(SingleFrameType frameType);
}
