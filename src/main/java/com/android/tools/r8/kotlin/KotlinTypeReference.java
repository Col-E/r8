// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;

/**
 * To account for invalid type references in kotlin metadata, the class KotlinTypeReference will
 * either hold a DexType reference, or a String, with the original name reference, which is not a
 * valid jvm descriptor/name. The values will be disjoint.
 */
class KotlinTypeReference {

  private final DexType known;
  private final String unknown;

  private KotlinTypeReference(DexType known) {
    this.known = known;
    this.unknown = null;
    assert known != null;
  }

  private KotlinTypeReference(String unknown) {
    this.known = null;
    this.unknown = unknown;
    assert unknown != null;
  }

  static KotlinTypeReference createFromBinaryName(
      String binaryName, DexDefinitionSupplier definitionSupplier) {
    if (DescriptorUtils.isValidBinaryName(binaryName)) {
      return createFromDescriptor(
          DescriptorUtils.getDescriptorFromClassBinaryName(binaryName), definitionSupplier);
    }
    return new KotlinTypeReference(binaryName);
  }

  static KotlinTypeReference createFromDescriptor(
      String descriptor, DexDefinitionSupplier definitionSupplier) {
    if (DescriptorUtils.isDescriptor(descriptor)) {
      DexType type = definitionSupplier.dexItemFactory().createType(descriptor);
      // Lookup the definition, ignoring the result. This populates the sets in the Enqueuer.
      if (type.isClassType()) {
        definitionSupplier.definitionFor(type);
      }
      return new KotlinTypeReference(type);
    }
    return new KotlinTypeReference(descriptor);
  }

  String toRenamedDescriptorOrDefault(
      AppView<AppInfoWithLiveness> appView, NamingLens namingLens, String defaultValue) {
    if (unknown != null) {
      return unknown;
    }
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(known)) {
      return defaultValue;
    }
    DexString descriptor = namingLens.lookupDescriptor(known);
    if (descriptor != null) {
      return descriptor.toString();
    }
    return defaultValue;
  }

  String toRenamedBinaryNameOrDefault(
      AppView<AppInfoWithLiveness> appView, NamingLens namingLens, String defaultValue) {
    if (unknown != null) {
      // Unknown values are always on the input form, so we can just return it.
      return unknown;
    }
    String descriptor = toRenamedDescriptorOrDefault(appView, namingLens, defaultValue);
    if (descriptor == null) {
      return null;
    }
    if (descriptor.equals(defaultValue)) {
      // We assume that the default value passed in is already a binary name.
      return descriptor;
    }
    return DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
  }
}
