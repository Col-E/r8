// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.BiConsumer;

public abstract class ScopedMappingInformation extends MappingInformation {

  // Abstraction for the items referenced in a scope.
  // We should consider passing in a scope reference factory.
  // For reading we likely want to map directly to DexItem, whereas for writing we likely want
  // to map to java.lang.String with the post-minification names.
  public abstract static class ScopeReference {

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

  public abstract static class Builder<B extends Builder<B>> {
    public abstract String getId();

    public abstract B self();

    private final ImmutableList.Builder<ScopeReference> scope = ImmutableList.builder();

    public B deserializeFromJsonObject(
        JsonObject object,
        ScopeReference implicitSingletonScope,
        DiagnosticsHandler diagnosticsHandler,
        int lineNumber) {
      JsonArray scopeArray = object.getAsJsonArray(SCOPE_KEY);
      if (scopeArray != null) {
        for (JsonElement element : scopeArray) {
          addScopeReference(ScopeReference.fromReferenceString(element.getAsString()));
        }
      } else if (implicitSingletonScope != null) {
        addScopeReference(implicitSingletonScope);
      } else {
        diagnosticsHandler.info(
            MappingInformationDiagnostics.noKeyForObjectWithId(
                lineNumber, SCOPE_KEY, MAPPING_ID_KEY, getId()));
      }
      return self();
    }

    public B addScopeReference(ScopeReference reference) {
      scope.add(reference);
      return self();
    }

    public ImmutableList<ScopeReference> buildScope() {
      return scope.build();
    }
  }

  public static final String SCOPE_KEY = "scope";

  private final ImmutableList<ScopeReference> scopeReferences;

  public ScopedMappingInformation(ImmutableList<ScopeReference> scopeReferences) {
    super(NO_LINE_NUMBER);
    this.scopeReferences = scopeReferences;
    assert !scopeReferences.isEmpty() : "Expected a scope. Global scope not yet in use.";
  }

  protected abstract JsonObject serializeToJsonObject(JsonObject object);

  @Override
  public final boolean isScopedMappingInformation() {
    return true;
  }

  @Override
  public final ScopedMappingInformation asScopedMappingInformation() {
    return this;
  }

  public void forEach(BiConsumer<ScopeReference, MappingInformation> fn) {
    for (ScopeReference reference : scopeReferences) {
      fn.accept(reference, this);
    }
  }

  @Override
  public final String serialize() {
    JsonObject object = serializeToJsonObject(new JsonObject());
    JsonArray scopeArray = new JsonArray();
    scopeReferences.forEach(ref -> scopeArray.add(ref.toReferenceString()));
    object.add(SCOPE_KEY, scopeArray);
    return object.toString();
  }
}
