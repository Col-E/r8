// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;

public abstract class ScopedMappingInformation {

  // Abstraction for the items referenced in a scope.
  // We should consider passing in a scope reference factory.
  // For reading we likely want to map directly to DexItem, whereas for writing we likely want
  // to map to java.lang.String with the post-minification names.
  public abstract static class ScopeReference {

    public static ScopeReference globalScope() {
      return GlobalScopeReference.INSTANCE;
    }

    public static ScopeReference fromClassReference(ClassReference reference) {
      return new ClassScopeReference(reference);
    }

    // Method for reading in the serialized reference format.
    public static ScopeReference fromReferenceString(String referenceString) {
      if (DescriptorUtils.isClassDescriptor(referenceString)) {
        return fromClassReference(Reference.classFromDescriptor(referenceString));
      }
      throw new Unimplemented("No support for reference: " + referenceString);
    }

    public boolean isGlobalScope() {
      return equals(ScopeReference.globalScope());
    }

    public abstract String toReferenceString();

    public abstract ClassReference getHolderReference();

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    @Override
    public String toString() {
      return toReferenceString();
    }
  }

  public static class GlobalScopeReference extends ScopeReference {
    private static final GlobalScopeReference INSTANCE = new GlobalScopeReference();

    @Override
    public String toReferenceString() {
      throw new Unreachable();
    }

    @Override
    public String toString() {
      return "<global-scope>";
    }

    @Override
    public ClassReference getHolderReference() {
      throw new Unreachable();
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public static class ClassScopeReference extends ScopeReference {
    private final ClassReference reference;

    public ClassScopeReference(ClassReference reference) {
      assert reference != null;
      this.reference = reference;
    }

    @Override
    public String toReferenceString() {
      return reference.getDescriptor();
    }

    @Override
    public ClassReference getHolderReference() {
      return reference;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ClassScopeReference
          && reference.equals(((ClassScopeReference) other).reference);
    }

    @Override
    public int hashCode() {
      return reference.hashCode();
    }
  }

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
