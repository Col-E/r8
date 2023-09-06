// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.KeepInfo.Builder;

/** Immutable keep requirements for a member. */
@SuppressWarnings("BadImport")
public abstract class KeepMemberInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>>
    extends KeepInfo<B, K> {

  KeepMemberInfo(B builder) {
    super(builder);
  }

  @SuppressWarnings("BadImport")
  public boolean isKotlinMetadataRemovalAllowed(
      DexProgramClass holder, GlobalKeepInfoConfiguration configuration) {
    // Checking the holder for missing kotlin information relies on the holder being processed
    // before members.
    return holder.getKotlinInfo().isNoKotlinInformation() || !isPinned(configuration);
  }
}
