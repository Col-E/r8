// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticMarker;
import java.util.List;
import java.util.function.Predicate;

/** Kind of the application class. Can be program, classpath or library. */
public class ClassKind<C extends DexClass> {
  public static ClassKind<DexProgramClass> PROGRAM =
      new ClassKind<>(
          (type,
              originKind,
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
              classAnnotations,
              staticFields,
              instanceFields,
              directMethods,
              virtualMethods,
              skipNameValidationForTesting,
              checksumSupplier,
              syntheticMarker) ->
              new DexProgramClass(
                  type,
                  originKind,
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
                  classAnnotations,
                  staticFields,
                  instanceFields,
                  MethodCollectionFactory.fromMethods(directMethods, virtualMethods),
                  skipNameValidationForTesting,
                  checksumSupplier,
                  syntheticMarker),
          DexClass::isProgramClass);
  public static ClassKind<DexClasspathClass> CLASSPATH =
      new ClassKind<>(
          (type,
              kind,
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
              directMethods,
              virtualMethods,
              skipNameValidationForTesting,
              checksumSupplier,
              syntheticMarker) ->
              new DexClasspathClass(
                  type,
                  kind,
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
                  skipNameValidationForTesting),
          DexClass::isClasspathClass);
  public static final ClassKind<DexLibraryClass> LIBRARY =
      new ClassKind<>(
          (type,
              kind,
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
              directMethods,
              virtualMethods,
              skipNameValidationForTesting,
              checksumSupplier,
              syntheticMarker) ->
              new DexLibraryClass(
                  type,
                  kind,
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
                  skipNameValidationForTesting),
          DexClass::isLibraryClass);

  private interface Factory<C extends DexClass> {
    C create(
        DexType type,
        Kind kind,
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
        DexEncodedMethod[] directMethods,
        DexEncodedMethod[] virtualMethods,
        boolean skipNameValidationForTesting,
        ChecksumSupplier checksumSupplier,
        SyntheticMarker syntheticMarker);
  }

  private final Factory<C> factory;
  private final Predicate<DexClass> check;

  ClassKind(Factory<C> factory, Predicate<DexClass> check) {
    this.factory = factory;
    this.check = check;
  }

  public C create(
      DexType type,
      Kind kind,
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
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods,
      boolean skipNameValidationForTesting,
      ChecksumSupplier checksumSupplier,
      SyntheticMarker syntheticMarker) {
    return factory.create(
        type,
        kind,
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
        directMethods,
        virtualMethods,
        skipNameValidationForTesting,
        checksumSupplier,
        syntheticMarker);
  }

  public boolean isOfKind(DexClass clazz) {
    return check.test(clazz);
  }
}
