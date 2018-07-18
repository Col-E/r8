// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.google.common.collect.HashBasedTable;
import java.util.BitSet;
import kotlinx.metadata.jvm.KotlinClassMetadata;

// Provides access to kotlin information.
public abstract class KotlinInfo<MetadataKind extends KotlinClassMetadata> {
  MetadataKind metadata;
  final HashBasedTable<String, String, BitSet> nonNullparamHints = HashBasedTable.create();

  KotlinInfo() {
  }

  KotlinInfo(MetadataKind metadata) {
    processMetadata(metadata);
    this.metadata = metadata;
  }

  // Subtypes will define how to process the given metadata.
  abstract void processMetadata(MetadataKind metadata);

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

  public boolean hasNonNullParameterHints() {
    return !nonNullparamHints.isEmpty();
  }

  public BitSet lookupNonNullParameterHint(String name, String descriptor) {
    return nonNullparamHints.get(name, descriptor);
  }
}
