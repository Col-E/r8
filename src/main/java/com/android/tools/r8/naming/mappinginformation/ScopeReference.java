// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;

/** Abstraction for the items referenced in a scope. */
public abstract class ScopeReference {

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
}
