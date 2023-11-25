// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.utils.ArrayUtils;

public class MethodBoxingStatus {

  public static final MethodBoxingStatus NONE_UNBOXABLE = new MethodBoxingStatus(null, null);

  private final ValueBoxingStatus returnStatus;
  private final ValueBoxingStatus[] argStatuses;

  public static MethodBoxingStatus create(
      ValueBoxingStatus returnStatus, ValueBoxingStatus[] argStatuses) {
    if (returnStatus.isNotUnboxable()
        && ArrayUtils.all(argStatuses, ValueBoxingStatus.NOT_UNBOXABLE)) {
      return NONE_UNBOXABLE;
    }
    return new MethodBoxingStatus(returnStatus, argStatuses);
  }

  private MethodBoxingStatus(ValueBoxingStatus returnStatus, ValueBoxingStatus[] argStatuses) {
    this.returnStatus = returnStatus;
    this.argStatuses = argStatuses;
  }

  public MethodBoxingStatus merge(MethodBoxingStatus other) {
    if (isNoneUnboxable() || other.isNoneUnboxable()) {
      return NONE_UNBOXABLE;
    }
    assert argStatuses.length == other.argStatuses.length;
    ValueBoxingStatus[] newArgStatuses = new ValueBoxingStatus[argStatuses.length];
    for (int i = 0; i < other.argStatuses.length; i++) {
      newArgStatuses[i] = other.argStatuses[i].merge(argStatuses[i]);
    }
    return create(returnStatus.merge(other.returnStatus), newArgStatuses);
  }

  public boolean isNoneUnboxable() {
    return this == NONE_UNBOXABLE;
  }

  public ValueBoxingStatus getReturnStatus() {
    assert !isNoneUnboxable();
    return returnStatus;
  }

  public ValueBoxingStatus getArgStatus(int i) {
    assert !isNoneUnboxable();
    return argStatuses[i];
  }

  public ValueBoxingStatus[] getArgStatuses() {
    assert !isNoneUnboxable();
    return argStatuses;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MethodBoxingStatus[");
    if (this == NONE_UNBOXABLE) {
      sb.append("NONE_UNBOXABLE");
    } else {
      for (int i = 0; i < argStatuses.length; i++) {
        if (argStatuses[i].mayBeUnboxable()) {
          sb.append(i).append(":").append(argStatuses[i]).append(";");
        }
      }
      if (returnStatus.mayBeUnboxable()) {
        sb.append("ret").append(":").append(returnStatus).append(";");
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
