// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;

public abstract class ScopedMappingInformation {

  private static final MapVersion SCOPE_SUPPORTED = MapVersion.MapVersionExperimental;
  public static final String SCOPE_KEY = "scope";

  public static List<ScopeReference> deserializeScope(
      JsonObject object,
      ScopeReference implicitSingletonScope,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      MapVersion version) {
    // Prior to support, the scope is always the implicit scope.
    if (version.isLessThan(SCOPE_SUPPORTED)) {
      return Collections.singletonList(implicitSingletonScope);
    }
    // If the scope key is absent, the implicit scope is assumed.
    JsonArray scopeArray = object.getAsJsonArray(SCOPE_KEY);
    if (scopeArray == null) {
      return Collections.singletonList(implicitSingletonScope);
    }
    ImmutableList.Builder<ScopeReference> builder = ImmutableList.builder();
    for (JsonElement element : scopeArray) {
      builder.add(ScopeReference.fromReferenceString(element.getAsString()));
    }
    return builder.build();
  }

  public static void serializeScope(
      JsonObject object,
      ScopeReference currentImplicitScope,
      List<ScopeReference> scopeReferences,
      MapVersion version) {
    assert !scopeReferences.isEmpty();
    // If emitting a non-experimental version the scope is always implicit.
    if (version.isLessThan(SCOPE_SUPPORTED)) {
      assert currentImplicitScope.equals(scopeReferences.get(0));
      return;
    }
    // If the scope matches the implicit scope don't add it explicitly.
    if (scopeReferences.size() == 1 && scopeReferences.get(0).equals(currentImplicitScope)) {
      return;
    }
    JsonArray scopeArray = new JsonArray();
    scopeReferences.forEach(ref -> scopeArray.add(ref.toReferenceString()));
    object.add(SCOPE_KEY, scopeArray);
  }
}
