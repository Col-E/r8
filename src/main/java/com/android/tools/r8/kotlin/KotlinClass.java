// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toKmType;
import static kotlinx.metadata.Flag.IS_SEALED;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
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

  DexField companionObject = null;
  DexProgramClass hostClass = null;

  static KotlinClass fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.Class;
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) kotlinClassMetadata;
    return new KotlinClass(kClass, clazz);
  }

  private KotlinClass(KotlinClassMetadata.Class metadata, DexClass clazz) {
    super(metadata, clazz);
  }

  void foundCompanionObject(DexEncodedField companionObject) {
    // Companion cannot be nested. If this class is a host (and about to store a field that holds
    // a companion object), it should not have a host class.
    assert hostClass == null;
    this.companionObject = companionObject.field;
  }

  boolean hasCompanionObject() {
    return companionObject != null;
  }

  DexType getCompanionObjectType() {
    return hasCompanionObject() ? companionObject.type : null;
  }

  void linkHostClass(DexProgramClass hostClass) {
    // Companion cannot be nested. If this class is a companion object (and about to link to its
    // host class), it should not have a companion object.
    assert companionObject == null;
    this.hostClass = hostClass;
  }

  @Override
  void processMetadata(KotlinClassMetadata.Class metadata) {
    kmClass = metadata.toKmClass();
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    KotlinMetadataSynthesizer synthesizer = new KotlinMetadataSynthesizer(appView, lens, this);
    if (appView.options().enableKotlinMetadataRewritingForRenamedClasses
        && lens.lookupType(clazz.type, appView.dexItemFactory()) != clazz.type) {
      String renamedClassifier = synthesizer.toRenamedClassifier(clazz.type);
      if (renamedClassifier != null) {
        assert !kmClass.getName().equals(renamedClassifier);
        kmClass.setName(renamedClassifier);
      }
    }

    ClassTypeSignatureToRenamedKmTypeConverter converter =
        new ClassTypeSignatureToRenamedKmTypeConverter(
            appView, getTypeParameters(), synthesizer::toRenamedClassifier);

    // Rewriting upward hierarchy.
    List<KmType> superTypes = kmClass.getSupertypes();
    superTypes.clear();
    for (DexType itfType : clazz.interfaces.values) {
      // TODO(b/129925954): Use GenericSignature.ClassSignature#superInterfaceSignatures
      KmType kmType = synthesizer.toRenamedKmType(itfType, null, null, converter);
      if (kmType != null) {
        superTypes.add(kmType);
      }
    }
    assert clazz.superType != null;
    if (clazz.superType != appView.dexItemFactory().objectType) {
      // TODO(b/129925954): Use GenericSignature.ClassSignature#superClassSignature
      KmType kmTypeForSupertype =
          synthesizer.toRenamedKmType(clazz.superType, null, null, converter);
      if (kmTypeForSupertype != null) {
        superTypes.add(kmTypeForSupertype);
      }
    } else if (clazz.isInterface()) {
      superTypes.add(toKmType(addKotlinPrefix("Any;")));
    }

    // Rewriting downward hierarchies: nested, including companion class.
    // Note that `kotlinc` uses these nested classes to determine which classes to look up when
    // resolving declarations in the companion object, e.g., Host.Companion.prop and Host.prop.
    // Thus, users (in particular, library developers) should keep InnerClasses and EnclosingMethod
    // attributes if declarations in the companion need to be exposed.
    List<String> nestedClasses = kmClass.getNestedClasses();
    nestedClasses.clear();
    for (InnerClassAttribute innerClassAttribute : clazz.getInnerClasses()) {
      // Skip InnerClass attribute for itself.
      // Otherwise, an inner class would have itself as a nested class.
      if (clazz.getInnerClassAttributeForThisClass() == innerClassAttribute) {
        continue;
      }
      DexString renamedInnerName = lens.lookupInnerName(innerClassAttribute, appView.options());
      if (renamedInnerName != null) {
        nestedClasses.add(renamedInnerName.toString());
      }
    }

    // Rewriting downward hierarchies: sealed.
    List<String> sealedSubclasses = kmClass.getSealedSubclasses();
    sealedSubclasses.clear();
    if (IS_SEALED.invoke(kmClass.getFlags())) {
      for (DexType subtype : appView.appInfo().allImmediateSubtypes(clazz.type)) {
        String classifier = synthesizer.toRenamedClassifier(subtype);
        if (classifier != null) {
          sealedSubclasses.add(classifier);
        }
      }
    }

    if (!appView.options().enableKotlinMetadataRewritingForMembers) {
      return;
    }

    // Rewriting constructors.
    List<KmConstructor> constructors = kmClass.getConstructors();
    constructors.clear();
    for (DexEncodedMethod method : clazz.directMethods()) {
      if (!method.isInstanceInitializer()) {
        continue;
      }
      KmConstructor constructor = synthesizer.toRenamedKmConstructor(method);
      if (constructor != null) {
        constructors.add(constructor);
      }
    }

    // Rewriting companion object if any.
    if (kmClass.getCompanionObject() != null && hasCompanionObject()) {
      kmClass.setCompanionObject(lens.lookupName(companionObject).toString());
    }

    // TODO(b/151193864): enum entries

    rewriteDeclarationContainer(synthesizer);
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

  @Override
  public String toString(String indent) {
    StringBuilder sb = new StringBuilder(indent);
    appendKmSection(
        indent,
        "Metadata.Class",
        sb,
        newIndent -> {
          appendKmClass(newIndent, sb, kmClass);
        });
    return sb.toString();
  }
}
