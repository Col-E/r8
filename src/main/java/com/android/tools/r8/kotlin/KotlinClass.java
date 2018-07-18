// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.Flag.Property.IS_VAR;

import kotlinx.metadata.KmClassVisitor;
import kotlinx.metadata.KmConstructorVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClass extends KotlinInfo<KotlinClassMetadata.Class> {

  static KotlinClass fromKotlinClassMetadata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.Class;
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) kotlinClassMetadata;
    return new KotlinClass(kClass);
  }

  private KotlinClass(KotlinClassMetadata.Class metadata) {
    super(metadata);
  }

  @Override
  void processMetadata(KotlinClassMetadata.Class metadata) {
    // To avoid lazy parsing/verifying metadata.
    metadata.accept(new ClassVisitorForNonNullParameterHints());
  }

  private class ClassVisitorForNonNullParameterHints extends KmClassVisitor {
    @Override
    public KmFunctionVisitor visitFunction(int functionFlags, String functionName) {
      return new NonNullParameterHintCollector.FunctionVisitor(nonNullparamHints);
    }

    @Override
    public KmConstructorVisitor visitConstructor(int ctorFlags) {
      return new NonNullParameterHintCollector.ConstructorVisitor(nonNullparamHints);
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
    return Kind.Class;
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public KotlinClass asClass() {
    return this;
  }

}
