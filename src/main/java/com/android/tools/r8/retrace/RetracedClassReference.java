// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;

@Keep
public interface RetracedClassReference {

  boolean isUnknown();

  boolean isKnown();

  String getTypeName();

  String getDescriptor();

  String getBinaryName();

  RetracedTypeReference getRetracedType();

  ClassReference getClassReference();
}
