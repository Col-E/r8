// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerEligibilityInfo;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;

public class ClassInlinerMethodConstraintAnalysis {

  public static ClassInlinerMethodConstraint analyze(
      ClassInlinerEligibilityInfo classInlinerEligibilityInfo,
      ParameterUsagesInfo parameterUsagesInfo) {
    boolean isEligibleForNewInstanceClassInlining =
        isEligibleForNewInstanceClassInlining(classInlinerEligibilityInfo);
    boolean isEligibleForStaticGetClassInlining =
        isEligibleForStaticGetClassInlining(classInlinerEligibilityInfo, parameterUsagesInfo);
    if (isEligibleForNewInstanceClassInlining) {
      if (isEligibleForStaticGetClassInlining) {
        return alwaysTrue();
      }
      return onlyNewInstanceClassInlining();
    }
    assert !isEligibleForStaticGetClassInlining;
    return alwaysFalse();
  }

  private static boolean isEligibleForNewInstanceClassInlining(
      ClassInlinerEligibilityInfo classInlinerEligibilityInfo) {
    return classInlinerEligibilityInfo != null;
  }

  private static boolean isEligibleForStaticGetClassInlining(
      ClassInlinerEligibilityInfo classInlinerEligibilityInfo,
      ParameterUsagesInfo parameterUsagesInfo) {
    if (classInlinerEligibilityInfo == null || parameterUsagesInfo == null) {
      return false;
    }
    if (classInlinerEligibilityInfo.hasMonitorOnReceiver) {
      // We will not be able to remove the monitor instruction afterwards.
      return false;
    }
    if (classInlinerEligibilityInfo.modifiesInstanceFields) {
      // The static instance could be accessed from elsewhere. Therefore, we cannot allow
      // side-effects to be removed and therefore cannot class inline method calls that modifies the
      // instance.
      return false;
    }
    ParameterUsage receiverUsage = parameterUsagesInfo.getParameterUsage(0);
    if (receiverUsage == null) {
      return false;
    }
    if (receiverUsage.hasFieldRead) {
      // We don't know the value of the field.
      return false;
    }
    return true;
  }

  private static AlwaysFalseClassInlinerMethodConstraint alwaysFalse() {
    return AlwaysFalseClassInlinerMethodConstraint.getInstance();
  }

  private static AlwaysTrueClassInlinerMethodConstraint alwaysTrue() {
    return AlwaysTrueClassInlinerMethodConstraint.getInstance();
  }

  private static OnlyNewInstanceClassInlinerMethodConstraint onlyNewInstanceClassInlining() {
    return OnlyNewInstanceClassInlinerMethodConstraint.getInstance();
  }
}
