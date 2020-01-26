// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toKmType;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedClassifier;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmConstructor;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmType;
import static kotlinx.metadata.Flag.IS_SEALED;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClass extends KotlinInfo<KotlinClassMetadata.Class> {

  KmClass kmClass;

  static KotlinClass fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.Class;
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) kotlinClassMetadata;
    return new KotlinClass(kClass, clazz);
  }

  private KotlinClass(KotlinClassMetadata.Class metadata, DexClass clazz) {
    super(metadata, clazz);
  }

  @Override
  void processMetadata() {
    assert !isProcessed;
    isProcessed = true;
    kmClass = metadata.toKmClass();
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    List<KmType> superTypes = kmClass.getSupertypes();
    superTypes.clear();
    for (DexType itfType : clazz.interfaces.values) {
      KmType kmType = toRenamedKmType(itfType, appView, lens);
      if (kmType != null) {
        superTypes.add(kmType);
      }
    }
    assert clazz.superType != null;
    if (clazz.superType != appView.dexItemFactory().objectType) {
      KmType kmTypeForSupertype = toRenamedKmType(clazz.superType, appView, lens);
      if (kmTypeForSupertype != null) {
        superTypes.add(kmTypeForSupertype);
      }
    } else if (clazz.isInterface()) {
      superTypes.add(toKmType(addKotlinPrefix("Any;")));
    }

    if (!appView.options().enableKotlinMetadataRewriting) {
      return;
    }
    List<KmConstructor> constructors = kmClass.getConstructors();
    constructors.clear();
    for (DexEncodedMethod method : clazz.directMethods()) {
      if (!method.isInstanceInitializer()) {
        continue;
      }
      KmConstructor constructor = toRenamedKmConstructor(method, appView, lens);
      if (constructor != null) {
        constructors.add(constructor);
      }
    }

    rewriteDeclarationContainer(kmClass, appView, lens);

    List<String> sealedSubclasses = kmClass.getSealedSubclasses();
    sealedSubclasses.clear();
    if (IS_SEALED.invoke(kmClass.getFlags())) {
      for (DexType subtype : appView.appInfo().allImmediateSubtypes(clazz.type)) {
        String classifier = toRenamedClassifier(subtype, appView, lens);
        if (classifier != null) {
          sealedSubclasses.add(classifier);
        }
      }
    }
  }

  @Override
  KotlinClassHeader createHeader() {
    KotlinClassMetadata.Class.Writer writer = new KotlinClassMetadata.Class.Writer();
    kmClass.accept(writer);
    return writer.write().getHeader();
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
