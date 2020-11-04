// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.identifiernamestring;

import static com.android.tools.r8.utils.FunctionUtils.applyOrElse;

import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;

public abstract class IdentifierNameStringLookupResult<R extends DexReference> {

  private final R reference;

  IdentifierNameStringLookupResult(R reference) {
    assert reference != null;
    this.reference = reference;
  }

  public static ClassForNameIdentifierNameStringLookupResult fromClassForName(DexType type) {
    return applyOrElse(type, ClassForNameIdentifierNameStringLookupResult::new, null);
  }

  public static ClassNameComparisonIdentifierNameStringLookupResult fromClassNameComparison(
      DexType type) {
    return applyOrElse(type, ClassNameComparisonIdentifierNameStringLookupResult::new, null);
  }

  public static DexTypeBasedConstStringIdentifierNameStringLookupResult fromDexTypeBasedConstString(
      DexType type) {
    return applyOrElse(type, DexTypeBasedConstStringIdentifierNameStringLookupResult::new, null);
  }

  public static DexMemberBasedConstStringIdentifierNameStringLookupResult
      fromDexMemberBasedConstString(DexMember<?, ?> member) {
    return applyOrElse(
        member, DexMemberBasedConstStringIdentifierNameStringLookupResult::new, null);
  }

  public static UncategorizedMemberIdentifierNameStringLookupResult fromUncategorized(
      DexMember<?, ?> member) {
    return applyOrElse(member, UncategorizedMemberIdentifierNameStringLookupResult::new, null);
  }

  public boolean isTypeResult() {
    return false;
  }

  public IdentifierNameStringTypeLookupResult asTypeResult() {
    return null;
  }

  public R getReference() {
    return reference;
  }
}
