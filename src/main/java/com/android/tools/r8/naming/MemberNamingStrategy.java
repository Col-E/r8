// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;

public interface MemberNamingStrategy {

  DexString next(DexMethod method, InternalNamingState internalState);

  DexString next(DexField field, InternalNamingState internalState);

  DexString getReservedNameOrDefault(
      DexEncodedMethod method, DexClass holder, DexString defaultValue);

  DexString getReservedNameOrDefault(
      DexEncodedField field, DexClass holder, DexString defaultValue);

  boolean allowMemberRenaming(DexClass holder);

  void reportReservationError(DexReference source, DexString name);
}
