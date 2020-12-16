// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FieldMultiset {

  private static class VisibilitySignature {
    private int publicVisible = 0;
    private int protectedVisible = 0;
    private int privateVisible = 0;
    private int packagePrivateVisible = 0;

    public void addAccessModifier(AccessFlags accessFlags) {
      if (accessFlags.isPublic()) {
        publicVisible++;
      } else if (accessFlags.isPrivate()) {
        privateVisible++;
      } else if (accessFlags.isPackagePrivate()) {
        packagePrivateVisible++;
      } else if (accessFlags.isProtected()) {
        protectedVisible++;
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VisibilitySignature that = (VisibilitySignature) o;
      return publicVisible == that.publicVisible
          && protectedVisible == that.protectedVisible
          && privateVisible == that.privateVisible
          && packagePrivateVisible == that.packagePrivateVisible;
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicVisible, protectedVisible, privateVisible, packagePrivateVisible);
    }
  }

  // This *must* not be an IdentityHashMap, because hash equality does not work for the values.
  private final Map<DexType, VisibilitySignature> fields = new HashMap<>();

  public FieldMultiset(DexProgramClass clazz) {
    for (DexEncodedField field : clazz.instanceFields()) {
      fields
          .computeIfAbsent(field.type(), ignore -> new VisibilitySignature())
          .addAccessModifier(field.getAccessFlags());
    }
  }

  public FieldMultiset(Iterable<DexEncodedField> instanceFields) {
    for (DexEncodedField field : instanceFields) {
      fields
          .computeIfAbsent(field.type(), ignore -> new VisibilitySignature())
          .addAccessModifier(field.getAccessFlags());
    }
  }

  @Override
  public int hashCode() {
    return fields.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof FieldMultiset) {
      FieldMultiset other = (FieldMultiset) object;
      return fields.equals(other.fields);
    } else {
      return false;
    }
  }
}
