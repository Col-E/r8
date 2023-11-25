// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public interface DexDefinitionSupplier {

  /**
   * Lookup for the definition of a type independent of context.
   *
   * <p>This will make use of the compilers program, library and classpath precedence.
   *
   * @param type Type to look up the defintion for.
   * @return Definition of the type or null if no definition exists.
   */
  default DexClass contextIndependentDefinitionFor(DexType type) {
    return definitionFor(type);
  }

  /**
   * Lookup for the definition(s) of a type independent of context.
   *
   * <p>This will return multiple results if found in the precedence of library, program and
   * classpath.
   *
   * @param type Type to look up the definition for.
   * @return A {@link ClassResolutionResult} describing the result.
   */
  ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type);

  /**
   * Lookup for the definition of a type from a given context.
   *
   * <p>This ensures that a context overrides the usual lookup precedence if looking up itself.
   *
   * @param type Type to look up a definition for.
   * @param context Context from which the lookup is taking place.
   * @return Definition of the type or null if no definition exists.
   */
  @SuppressWarnings("ReferenceEquality")
  default DexClass definitionFor(DexType type, DexProgramClass context) {
    return type == context.type ? context : contextIndependentDefinitionFor(type);
  }

  default DexClass definitionFor(DexType type, ProgramMethod context) {
    return definitionFor(type, context.getHolder());
  }

  /**
   * Lookup for the program definition of a type from a given context.
   *
   * <p>This ensures that a context overrides the usual lookup precedence if looking up itself.
   *
   * @param type Type to look up a definition for.
   * @param context Context from which the lookup is taking place.
   * @return Definition of the type if it is a program type or null if not or no definition exists.
   */
  default DexProgramClass programDefinitionFor(DexType type, DexProgramClass context) {
    return DexProgramClass.asProgramClassOrNull(definitionFor(type, context));
  }

  default DexProgramClass programDefinitionFor(DexType type, ProgramMethod context) {
    return programDefinitionFor(type, context.getHolder());
  }

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexEncodedMember<D, R> member, ProgramMethod context) {
    return definitionForHolder(member.getReference(), context.getHolder());
  }

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexEncodedMember<D, R> member, DexProgramClass context) {
    return definitionForHolder(member.getReference(), context);
  }

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexMember<D, R> member, ProgramMethod context) {
    return definitionFor(member.holder, context.getHolder());
  }

  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexMember<D, R> member, DexProgramClass context) {
    return definitionFor(member.holder, context);
  }

  // Use definitionFor with a context or contextIndependentDefinitionFor without.
  @Deprecated
  DexClass definitionFor(DexType type);

  default boolean hasDefinitionFor(DexType type) {
    return definitionFor(type) != null;
  }

  default DexClassAndField definitionFor(DexField field) {
    DexClass holder = definitionFor(field.getHolderType());
    return holder != null ? holder.lookupClassField(field) : null;
  }

  default DexClassAndMethod definitionFor(DexMethod method) {
    DexClass holder = definitionFor(method.getHolderType());
    return holder != null ? holder.lookupClassMethod(method) : null;
  }

  default boolean hasDefinitionFor(DexMethod method) {
    return definitionFor(method) != null;
  }

  @SuppressWarnings("ReferenceEquality")
  default ClassResolutionResult definitionForWithResolutionResult(
      DexType type, DexProgramClass context) {
    assert context.type != type || ClassResolutionResult.builder().add(context).build() == context;
    return context.type == type
        ? context
        : contextIndependentDefinitionForWithResolutionResult(type);
  }

  // Use programDefinitionFor with a context.
  @Deprecated
  default DexProgramClass definitionForProgramType(DexType type) {
    return DexProgramClass.asProgramClassOrNull(definitionFor(type));
  }

  // Use definitionForHolder with a context.
  @Deprecated
  default <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      DexClass definitionForHolder(DexMember<D, R> member) {
    return definitionFor(member.holder);
  }

  DexItemFactory dexItemFactory();
}
