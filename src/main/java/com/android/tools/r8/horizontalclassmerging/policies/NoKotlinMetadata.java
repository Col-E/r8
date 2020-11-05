// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.google.common.collect.Streams;

public class NoKotlinMetadata extends SingleClassPolicy {

  public NoKotlinMetadata() {}

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    assert verifyNoUnexpectedKotlinInfo(clazz);
    return true;
  }

  private boolean verifyNoUnexpectedKotlinInfo(DexProgramClass clazz) {
    if (clazz.getKotlinInfo().isNoKotlinInformation()) {
      assert verifyNoUnexpectedKotlinMemberInfo(clazz);
      return true;
    }
    assert clazz.getKotlinInfo().isSyntheticClass()
        && clazz.getKotlinInfo().asSyntheticClass().isLambda();
    return true;
  }

  private boolean verifyNoUnexpectedKotlinMemberInfo(DexProgramClass clazz) {
    assert Streams.stream(clazz.members())
        .allMatch(member -> member.getKotlinMemberInfo().isNoKotlinInformation());
    return true;
  }
}
