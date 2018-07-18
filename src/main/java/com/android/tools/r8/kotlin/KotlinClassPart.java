// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.Flag.Property.IS_VAR;

import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmPackageVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassPart extends KotlinInfo<KotlinClassMetadata.MultiFileClassPart> {

  static KotlinClassPart fromKotlinClassMetdata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.MultiFileClassPart;
    KotlinClassMetadata.MultiFileClassPart multiFileClassPart =
        (KotlinClassMetadata.MultiFileClassPart) kotlinClassMetadata;
    return new KotlinClassPart(multiFileClassPart);
  }

  private KotlinClassPart(KotlinClassMetadata.MultiFileClassPart metadata) {
    super(metadata);
  }

  @Override
  void processMetadata(KotlinClassMetadata.MultiFileClassPart metadata) {
    // To avoid lazy parsing/verifying metadata.
    metadata.accept(new PackageVisitorForNonNullParameterHints());
  }

  private class PackageVisitorForNonNullParameterHints extends KmPackageVisitor {
    @Override
    public KmFunctionVisitor visitFunction(int functionFlags, String functionName) {
      return new NonNullParameterHintCollector.FunctionVisitor(nonNullparamHints);
    }

    @Override
    public KmPropertyVisitor visitProperty(
        int propertyFlags, String name, int getterFlags, int setterFlags) {
      if (IS_VAR.invoke(propertyFlags)) {
        return new NonNullParameterHintCollector.PropertyVisitor(nonNullparamHints);
      }
      return null;
    }
  }

  @Override
  public Kind getKind() {
    return Kind.Part;
  }

  @Override
  public boolean isClassPart() {
    return true;
  }

  @Override
  public KotlinClassPart asClassPart() {
    return this;
  }

}
