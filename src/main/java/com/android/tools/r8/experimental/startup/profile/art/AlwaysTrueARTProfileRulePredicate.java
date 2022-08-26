// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile.art;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.startup.ARTProfileClassRuleInfo;
import com.android.tools.r8.startup.ARTProfileMethodRuleInfo;
import com.android.tools.r8.startup.ARTProfileRulePredicate;

public class AlwaysTrueARTProfileRulePredicate implements ARTProfileRulePredicate {

  @Override
  public boolean testClassRule(
      ClassReference classReference, ARTProfileClassRuleInfo classRuleInfo) {
    return true;
  }

  @Override
  public boolean testMethodRule(
      MethodReference methodReference, ARTProfileMethodRuleInfo methodRuleInfo) {
    return true;
  }
}
