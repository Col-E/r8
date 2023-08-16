// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.utils.OptionalBool;

public interface ConstantOrNonConstantNumberValue {

  boolean maybeContainsInt(int value);

  long getMinInclusive();

  OptionalBool isSubsetOf(int[] values);

  ConstantOrNonConstantNumberValue asConstantOrNonConstantNumberValue();

  boolean isDefiniteBitsNumberValue();

  DefiniteBitsNumberValue asDefiniteBitsNumberValue();

  boolean isSingleNumberValue();

  SingleNumberValue asSingleNumberValue();

  boolean isNonConstantNumberValue();

  NonConstantNumberValue asNonConstantNumberValue();

  boolean isNumberFromIntervalValue();

  NumberFromIntervalValue asNumberFromIntervalValue();

  boolean isNumberFromSetValue();

  NumberFromSetValue asNumberFromSetValue();

  boolean mayOverlapWith(ConstantOrNonConstantNumberValue other);
}
