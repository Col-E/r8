// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfFrame.SingleInitializedType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public interface SingleFrameType {

  FrameType asFrameType();

  boolean isInitialized();

  DexType getInitializedType(DexItemFactory dexItemFactory);

  boolean isInt();

  SingleInitializedType asSingleInitializedType();

  boolean isNullType();

  boolean isOneWord();

  boolean isPrimitive();

  boolean isUninitializedNew();

  DexType getUninitializedNewType();

  boolean isUninitializedObject();

  SingleFrameType join(SingleFrameType frameType);
}
