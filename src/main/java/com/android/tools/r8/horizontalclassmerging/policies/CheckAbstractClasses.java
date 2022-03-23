// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses.AbstractClassification;
import com.android.tools.r8.utils.InternalOptions;

public class CheckAbstractClasses extends MultiClassSameReferencePolicy<AbstractClassification> {

  enum AbstractClassification {
    ABSTRACT,
    NOT_ABSTRACT
  }

  private final InternalOptions options;

  public CheckAbstractClasses(AppView<?> appView) {
    this.options = appView.options();
  }

  @Override
  public String getName() {
    return "CheckAbstractClasses";
  }

  @Override
  public boolean shouldSkipPolicy() {
    // We can just make the target class non-abstract if one of the classes in the group
    // is non-abstract.
    return options.canUseAbstractMethodOnNonAbstractClass();
  }

  @Override
  public AbstractClassification getMergeKey(DexProgramClass clazz) {
    return clazz.isAbstract()
        ? AbstractClassification.ABSTRACT
        : AbstractClassification.NOT_ABSTRACT;
  }
}
