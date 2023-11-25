// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.TypeReference;

/** Inspector for a JVM representable value. */
@KeepForApi
public interface ValueInspector {

  /** Get the type reference describing the type of the value. */
  TypeReference getTypeReference();

  boolean isPrimitive();

  boolean isBooleanValue();

  boolean isByteValue();

  boolean isCharValue();

  boolean isShortValue();

  boolean isIntValue();

  boolean isLongValue();

  boolean isFloatValue();

  boolean isDoubleValue();

  boolean isStringValue();

  BooleanValueInspector asBooleanValue();

  ByteValueInspector asByteValue();

  CharValueInspector asCharValue();

  ShortValueInspector asShortValue();

  IntValueInspector asIntValue();

  LongValueInspector asLongValue();

  FloatValueInspector asFloatValue();

  DoubleValueInspector asDoubleValue();

  StringValueInspector asStringValue();
}
