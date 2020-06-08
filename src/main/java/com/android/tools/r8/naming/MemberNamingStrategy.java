// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramField;
import java.util.function.BiPredicate;

public interface MemberNamingStrategy {

  DexString next(
      DexEncodedMethod method,
      InternalNamingState internalState,
      BiPredicate<DexString, DexMethod> isAvailable);

  DexString next(
      ProgramField field,
      InternalNamingState internalState,
      BiPredicate<DexString, ProgramField> isAvailable);

  DexString getReservedName(DexEncodedMethod method, DexClass holder);

  DexString getReservedName(DexEncodedField field, DexClass holder);

  boolean allowMemberRenaming(DexClass holder);
}
