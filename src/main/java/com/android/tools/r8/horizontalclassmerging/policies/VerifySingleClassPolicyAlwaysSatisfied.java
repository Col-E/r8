// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.utils.InternalOptions;

public class VerifySingleClassPolicyAlwaysSatisfied extends SingleClassPolicy {

  private final SingleClassPolicy policy;

  public VerifySingleClassPolicyAlwaysSatisfied(SingleClassPolicy policy) {
    this.policy = policy;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    assert policy.canMerge(program) : "Verification of single class policies failed";
    return true;
  }

  @Override
  public String getName() {
    return "VerifySingleClassPolicyAlwaysSatisfied(" + policy.getName() + ")";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !InternalOptions.assertionsEnabled() || policy.shouldSkipPolicy();
  }
}
