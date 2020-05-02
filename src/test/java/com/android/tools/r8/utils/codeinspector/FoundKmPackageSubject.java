// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmPackage;

public class FoundKmPackageSubject extends KmPackageSubject
    implements FoundKmDeclarationContainerSubject {
  private final CodeInspector codeInspector;
  private final DexClass clazz;
  private final KmPackage kmPackage;

  FoundKmPackageSubject(CodeInspector codeInspector, DexClass clazz, KmPackage kmPackage) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.kmPackage = kmPackage;
  }

  @Override
  public DexClass getDexClass() {
    return clazz;
  }

  @Override
  public CodeInspector codeInspector() {
    return codeInspector;
  }

  @Override
  public KmDeclarationContainer getKmDeclarationContainer() {
    return kmPackage;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    // TODO(b/151194785): This should return `true` conditionally if we start synthesizing @Metadata
    //   from scratch.
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    KotlinMetadataWriter.appendKmPackage("", sb, kmPackage);
    return sb.toString();
  }
}
