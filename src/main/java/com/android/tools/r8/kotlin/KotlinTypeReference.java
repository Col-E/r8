// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.DescriptorUtils;

/**
 * To account for invalid type references in kotlin metadata, the class KotlinTypeReference will
 * either hold a DexType reference, or a String, with the original name reference, which is not a
 * valid jvm descriptor/name. The values will be disjoint.
 */
class KotlinTypeReference implements EnqueuerMetadataTraceable {

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

  public DexType getKnown() {
    return known;
  }

  static KotlinTypeReference fromBinaryName(String binaryName, DexItemFactory factory) {
    if (DescriptorUtils.isValidBinaryName(binaryName)) {
      return fromDescriptor(
          DescriptorUtils.getDescriptorFromClassBinaryName(binaryName), factory, binaryName);
    }
    return new KotlinTypeReference(binaryName);
  }

  static KotlinTypeReference fromDescriptor(String descriptor, DexItemFactory factory) {
    return fromDescriptor(descriptor, factory, descriptor);
  }

  static KotlinTypeReference fromDescriptor(
      String descriptor, DexItemFactory factory, String unknownValue) {
    if (DescriptorUtils.isDescriptor(descriptor)) {
      DexType type = factory.createType(descriptor);
      return new KotlinTypeReference(type);
    }
    return new KotlinTypeReference(unknownValue);
  }

  String toRenamedDescriptorOrDefault(
      AppView<?> appView, NamingLens namingLens, String defaultValue) {
    if (unknown != null) {
      return unknown;
    }
    assert known != null;
    DexType rewrittenType = toRewrittenTypeOrNull(appView, known);
    if (rewrittenType == null) {
      return defaultValue;
    }
    return namingLens.lookupDescriptor(rewrittenType).toString();
  }

  String toRenamedBinaryNameOrDefault(
      AppView<?> appView, NamingLens namingLens, String defaultValue) {
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

  private static DexType toRewrittenTypeOrNull(AppView<?> appView, DexType type) {
    if (type.isArrayType()) {
      DexType rewrittenBaseType =
          toRewrittenTypeOrNull(appView, type.toBaseType(appView.dexItemFactory()));
      return rewrittenBaseType != null
          ? type.replaceBaseType(rewrittenBaseType, appView.dexItemFactory())
          : null;
    }
    if (!type.isClassType()) {
      return type;
    }
    DexType rewrittenType = appView.graphLens().lookupClassType(type);
    if (appView.appInfo().hasLiveness()
        && !appView.withLiveness().appInfo().isNonProgramTypeOrLiveProgramType(rewrittenType)) {
      return null;
    }
    return rewrittenType;
  }

  @Override
  public String toString() {
    return known != null ? known.descriptor.toString() : unknown;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (known != null && known.isClassType()) {
      // Lookup the definition, ignoring the result. This populates the sets in the Enqueuer.
      definitionSupplier.contextIndependentDefinitionFor(known);
    }
  }

  public DexType rewriteType(GraphLens graphLens) {
    if (known != null && known.isClassType()) {
      return graphLens.lookupClassType(known);
    }
    return null;
  }
}
