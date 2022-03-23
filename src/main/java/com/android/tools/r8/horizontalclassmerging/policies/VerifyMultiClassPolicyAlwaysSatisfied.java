// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.Collections;

public class VerifyMultiClassPolicyAlwaysSatisfied extends MultiClassPolicy {

  private final MultiClassPolicy policy;

  public VerifyMultiClassPolicyAlwaysSatisfied(MultiClassPolicy policy) {
    this.policy = policy;
  }

  @Override
  public String getName() {
    return "VerifyMultiClassPolicyAlwaysSatisfied(" + policy.getName() + ")";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !InternalOptions.assertionsEnabled() || policy.shouldSkipPolicy();
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    assert verifySameAppliedGroup(group);
    return Collections.singletonList(group);
  }

  private boolean verifySameAppliedGroup(MergeGroup group) {
    Collection<MergeGroup> applied = policy.apply(group);
    assert applied.size() == 1;
    MergeGroup appliedGroup = applied.iterator().next();
    assert appliedGroup.size() == group.size() && group.containsAll(appliedGroup);
    return true;
  }
}
