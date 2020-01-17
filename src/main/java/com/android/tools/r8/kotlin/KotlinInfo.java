// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmFunction;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmFunctionAsExtension;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

// Provides access to package/class-level Kotlin information.
public abstract class KotlinInfo<MetadataKind extends KotlinClassMetadata> {
  final MetadataKind metadata;
  final DexClass clazz;
  boolean isProcessed;

  KotlinInfo(MetadataKind metadata, DexClass clazz) {
    assert clazz != null;
    this.metadata = metadata;
    this.clazz = clazz;
    processMetadata();
  }

  // Subtypes will define how to process the given metadata.
  abstract void processMetadata();

  // Subtypes will define how to rewrite metadata after shrinking and minification.
  // Subtypes that represent subtypes of {@link KmDeclarationContainer} can use
  // {@link #rewriteDeclarationContainer} below.
  abstract void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens);

  abstract KotlinClassHeader createHeader();

  public enum Kind {
    Class, File, Synthetic, Part, Facade
  }

  public abstract Kind getKind();

  public boolean isClass() {
    return false;
  }

  public KotlinClass asClass() {
    return null;
  }

  public boolean isFile() {
    return false;
  }

  public KotlinFile asFile() {
    return null;
  }

  public boolean isSyntheticClass() {
    return false;
  }

  public KotlinSyntheticClass asSyntheticClass() {
    return null;
  }

  public boolean isClassPart() {
    return false;
  }

  public KotlinClassPart asClassPart() {
    return null;
  }

  public boolean isClassFacade() {
    return false;
  }

  public KotlinClassFacade asClassFacade() {
    return null;
  }

  boolean hasDeclarations() {
    return isClass() || isFile() || isClassPart();
  }

  @Override
  public String toString() {
    return (clazz != null ? clazz.toSourceString() : "<null class?!>")
        + ": " + metadata.toString();
  }

  // {@link KmClass} and {@link KmPackage} are inherited from {@link KmDeclarationContainer} that
  // abstract functions and properties. Rewriting of those portions can be unified here.
  void rewriteDeclarationContainer(
      KmDeclarationContainer kmDeclarationContainer,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    assert clazz != null;

    List<KmFunction> functions = kmDeclarationContainer.getFunctions();
    functions.clear();
    // TODO(b/70169921): clear property
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }

      if (method.isKotlinExtensionFunction()) {
        KmFunction extension = toRenamedKmFunctionAsExtension(method, appView, lens);
        if (extension != null) {
          functions.add(extension);
        }
        continue;
      }
      if (method.isKotlinFunction()) {
        KmFunction function = toRenamedKmFunction(method, appView, lens);
        if (function != null) {
          functions.add(function);
        }
        continue;
      }
      if (method.isKotlinExtensionProperty()) {
        // TODO(b/70169921): (extension) property
        continue;
      }
      if (method.isKotlinProperty()) {
        // TODO(b/70169921): (extension) property
        continue;
      }

      // TODO(b/70169921): What should we do for methods that fall into this category---no mark?
    }
    // TODO(b/70169921): (extension) companion
  }
}
