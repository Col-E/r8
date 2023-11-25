// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getKotlinLocalOrAnonymousNameFromDescriptor;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.function.Consumer;

/**
 * To account for invalid type references in kotlin metadata, the class KotlinTypeReference will
 * either hold a DexType reference, or a String, with the original name reference, which is not a
 * valid jvm descriptor/name. The values will be disjoint.
 */
class KotlinTypeReference implements EnqueuerMetadataTraceable {

  private final DexType known;
  private final String originalName;
  // A KotlinTypeReference may be either a Kotlin classifier or a binary name.
  // We need to maintain the right type to avoid unnecessary difference between original and
  // rewritten metadata.
  private final boolean exportAsKotlinClassifier;

  private KotlinTypeReference(
      String originalName, DexType known, boolean exportAsKotlinClassifier) {
    this.originalName = originalName;
    this.known = known;
    this.exportAsKotlinClassifier = exportAsKotlinClassifier;
    assert known != null;
  }

  private KotlinTypeReference(String originalName) {
    this.known = null;
    this.exportAsKotlinClassifier = false;
    this.originalName = originalName;
    assert originalName != null;
  }

  public DexType getKnown() {
    return known;
  }

  public String getOriginalName() {
    return originalName;
  }

  static KotlinTypeReference fromBinaryNameOrKotlinClassifier(
      String binaryNameOrKotlinClassifier, DexItemFactory factory, String originalName) {
    // Kotlin classifiers are valid binary names.
    if (DescriptorUtils.isValidBinaryName(binaryNameOrKotlinClassifier)) {
      boolean interpretAsKotlinClassifier =
          shouldBeInterpretedAsKotlinClassifier(binaryNameOrKotlinClassifier);
      return fromDescriptor(
          interpretAsKotlinClassifier
              ? DescriptorUtils.getDescriptorFromKotlinClassifier(binaryNameOrKotlinClassifier)
              : DescriptorUtils.getDescriptorFromClassBinaryName(binaryNameOrKotlinClassifier),
          factory,
          originalName,
          interpretAsKotlinClassifier);
    }
    return new KotlinTypeReference(binaryNameOrKotlinClassifier);
  }

  private static boolean shouldBeInterpretedAsKotlinClassifier(
      String binaryNameOrKotlinClassifier) {
    return binaryNameOrKotlinClassifier.contains(".");
  }

  static KotlinTypeReference fromDescriptor(String descriptor, DexItemFactory factory) {
    return fromDescriptor(descriptor, factory, descriptor, false);
  }

  static KotlinTypeReference fromDescriptor(
      String descriptor,
      DexItemFactory factory,
      String originalName,
      boolean exportAsKotlinClassifier) {
    if (DescriptorUtils.isDescriptor(descriptor)) {
      DexType type = factory.createType(descriptor);
      return new KotlinTypeReference(originalName, type, exportAsKotlinClassifier);
    }
    return new KotlinTypeReference(originalName);
  }

  boolean toRenamedDescriptorOrDefault(
      Consumer<String> rewrittenConsumer,
      AppView<?> appView,
      String defaultValue) {
    if (known == null) {
      rewrittenConsumer.accept(originalName);
      return false;
    }
    DexType rewrittenType = toRewrittenTypeOrNull(appView, known);
    if (rewrittenType == null) {
      String knownDescriptor = known.toDescriptorString();
      // Static known kotlin types can be pruned without rewriting to Any since the types are known
      // by kotlinc and kotlin reflect.
      if (ClassClassifiers.kotlinStaticallyKnownTypes.contains(knownDescriptor)) {
        rewrittenConsumer.accept(knownDescriptor);
        return false;
      } else {
        rewrittenConsumer.accept(defaultValue);
        return true;
      }
    }
    String renamedString = appView.getNamingLens().lookupDescriptor(rewrittenType).toString();
    rewrittenConsumer.accept(renamedString);
    return !known.toDescriptorString().equals(renamedString);
  }

  String toKotlinClassifier(boolean isLocalOrAnonymous) {
    if (known == null) {
      return originalName;
    }
    return getKotlinLocalOrAnonymousNameFromDescriptor(
        known.toDescriptorString(), isLocalOrAnonymous);
  }

  boolean toRenamedBinaryNameOrDefault(
      Consumer<String> rewrittenConsumer,
      AppView<?> appView,
      String defaultValue) {
    if (known == null) {
      // Unknown values are always on the input form, so we can just return it.
      rewrittenConsumer.accept(originalName);
      return false;
    }
    return toRenamedDescriptorOrDefault(
        descriptor -> {
          // We assume that the default value passed in is already a binary name.
          if (descriptor == null || descriptor.equals(defaultValue)) {
            rewrittenConsumer.accept(descriptor);
          } else {
            String rewritten =
                exportAsKotlinClassifier
                    ? DescriptorUtils.descriptorToKotlinClassifier(descriptor)
                    : DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
            rewrittenConsumer.accept(rewritten);
          }
        },
        appView,
        defaultValue);
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
    return known != null ? known.descriptor.toString() : originalName;
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
