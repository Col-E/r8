// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DexLibraryClass extends DexClass implements LibraryClass, Supplier<DexLibraryClass> {

  public DexLibraryClass(
      DexType type,
      ProgramResource.Kind kind,
      Origin origin,
      ClassAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      List<PermittedSubclassAttribute> permittedSubclasses,
      List<RecordComponentInfo> recordComponents,
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      ClassSignature classSignature,
      DexAnnotationSet annotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      MethodCollectionFactory methodCollectionFactory,
      boolean skipNameValidationForTesting) {
    super(
        sourceFile,
        interfaces,
        accessFlags,
        superType,
        type,
        staticFields,
        instanceFields,
        methodCollectionFactory,
        nestHost,
        nestMembers,
        permittedSubclasses,
        recordComponents,
        enclosingMember,
        innerClasses,
        classSignature,
        annotations,
        origin,
        skipNameValidationForTesting);
    assert Streams.stream(methods()).allMatch(DexLibraryClass::verifyLibraryMethod);
    assert Arrays.stream(staticFields).allMatch(DexLibraryClass::verifyLibraryField);
    assert Arrays.stream(instanceFields).allMatch(DexLibraryClass::verifyLibraryField);
    // Set all static field values to unknown. We don't want to use the value from the library
    // at compile time, as it can be different at runtime.
    for (DexEncodedField staticField : staticFields) {
      staticField.clearStaticValue();
    }
    assert kind == Kind.CF : "Invalid kind " + kind + " for library-path class " + type;
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  public static DexLibraryClass asLibraryClassOrNull(DexClass clazz) {
    return clazz != null ? clazz.asLibraryClass() : null;
  }

  private static boolean verifyLibraryMethod(DexEncodedMethod method) {
    assert !method.isClassInitializer();
    assert !method.isPrivateMethod();
    assert !method.hasCode();
    return true;
  }

  private static boolean verifyLibraryField(DexEncodedField field) {
    assert !field.isPrivate();
    assert !field.isStatic() || !field.hasExplicitStaticValue();
    return true;
  }

  @Override
  public void accept(
      Consumer<DexProgramClass> programClassConsumer,
      Consumer<DexClasspathClass> classpathClassConsumer,
      Consumer<DexLibraryClass> libraryClassConsumer) {
    libraryClassConsumer.accept(this);
  }

  @Override
  public String toString() {
    return type.toString() + "(library class)";
  }

  @Override
  public String toSourceString() {
    return type.toSourceString() + "(library class)";
  }

  @Override
  public void addDependencies(MixedSectionCollection collector) {
    // Should never happen but does not harm.
    assert false;
  }

  @Override
  public boolean isNotProgramClass() {
    return true;
  }

  @Override
  public boolean isLibraryClass() {
    return true;
  }

  @Override
  public DexLibraryClass asLibraryClass() {
    return this;
  }

  @Override
  public DexLibraryClass asClasspathOrLibraryClass() {
    return this;
  }

  @Override
  public KotlinClassLevelInfo getKotlinInfo() {
    throw new Unreachable("We should never consider metadata for library classes");
  }

  @Override
  public ClassKind<DexLibraryClass> getKind() {
    return ClassKind.LIBRARY;
  }

  @Override
  public DexLibraryClass get() {
    return this;
  }

  @Override
  boolean internalClassOrInterfaceMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    if (!seen.add(getType()) || ignore.test(getType())) {
      return false;
    }
    return isInterface()
        ? appView.options().libraryInterfacesMayHaveStaticInitialization
        : !appView.dexItemFactory().libraryClassesWithoutStaticInitialization.contains(type);
  }

  public static class Builder {

    // Required.
    private DexType type;
    private ClassAccessFlags accessFlags;

    // Optional.
    private Origin origin = Origin.unknown();
    private DexType superType;
    private DexTypeList interfaces = DexTypeList.empty();
    private DexString sourceFile = null;
    private NestHostClassAttribute nestHost = null;
    private List<NestMemberClassAttribute> nestMembers = Collections.emptyList();
    private List<PermittedSubclassAttribute> permittedSubclasses = Collections.emptyList();
    private List<RecordComponentInfo> recordComponents = Collections.emptyList();
    private EnclosingMethodAttribute enclosingMember = null;
    private List<InnerClassAttribute> innerClasses = Collections.emptyList();
    private ClassSignature classSignature = ClassSignature.noSignature();
    private DexAnnotationSet annotations = DexAnnotationSet.empty();
    private DexEncodedField[] staticFields = DexEncodedField.EMPTY_ARRAY;
    private DexEncodedField[] instanceFields = DexEncodedField.EMPTY_ARRAY;
    private DexEncodedMethod[] directMethods = DexEncodedMethod.EMPTY_ARRAY;
    private DexEncodedMethod[] virtualMethods = DexEncodedMethod.EMPTY_ARRAY;
    private boolean skipNameValidationForTesting;

    private Builder(DexItemFactory dexItemFactory) {
      this.superType = dexItemFactory.objectType;
      this.skipNameValidationForTesting = dexItemFactory.getSkipNameValidationForTesting();
    }

    public Builder setAccessFlags(ClassAccessFlags accessFlags) {
      this.accessFlags = accessFlags;
      return this;
    }

    public Builder setDirectMethods(Collection<DexEncodedMethod> directMethods) {
      this.directMethods = directMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
      return this;
    }

    public Builder setType(DexType type) {
      this.type = type;
      return this;
    }

    public DexLibraryClass build() {
      assert validate();
      return new DexLibraryClass(
          type,
          ProgramResource.Kind.CF,
          origin,
          accessFlags,
          superType,
          interfaces,
          sourceFile,
          nestHost,
          nestMembers,
          permittedSubclasses,
          recordComponents,
          enclosingMember,
          innerClasses,
          classSignature,
          annotations,
          staticFields,
          instanceFields,
          MethodCollectionFactory.fromMethods(directMethods, virtualMethods),
          skipNameValidationForTesting);
    }

    private boolean validate() {
      assert type != null;
      assert accessFlags != null;
      return true;
    }
  }
}
