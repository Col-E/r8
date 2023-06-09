// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * EnumLite proto enums include the following code: <code>
 * private static final EnumLiteMap<ProtoEnum> internalValueMap =
 *   new EnumLiteMap<ProtoEnum>() {
 *     public ProtoEnum findValueByNumber(int number) {
 *       return ProtoEnum.forNumber(number);
 *     }
 *   };
 * </code>
 *
 * <p>If the field internalValueMap on the EnumLite is effectively unused (never read), the
 * anonymous subclass of EnumLiteMap is effectively dead. R8 figures that out however too late,
 * during the second round of tree shaking, and the bridge findValueByNumber in the anonymous
 * subclass of EnumLiteMap prevents the EnumLite to be unboxed.
 *
 * <p>This class prematurely clears the virtual methods of the anonymous subclasses of EnumLiteMap,
 * when it can prove that the internalValueMap field of the corresponding EnumLite is never read.
 */
public class EnumLiteProtoShrinker {

  private AppView<AppInfoWithLiveness> appView;
  private ProtoReferences references;
  private Set<DexType> deadEnumLiteMaps = Sets.newIdentityHashSet();

  public EnumLiteProtoShrinker(AppView<AppInfoWithLiveness> appView, ProtoReferences references) {
    this.appView = appView;
    this.references = references;
  }

  public Set<DexType> getDeadEnumLiteMaps() {
    return deadEnumLiteMaps;
  }

  private DexField createInternalValueMapField(DexType holder) {
    return appView
        .dexItemFactory()
        .createField(holder, references.enumLiteMapType, references.internalValueMapFieldName);
  }

  public void clearDeadEnumLiteMaps(PrunedItems.Builder prunedItemsBuilder) {
    assert appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled();
    // The optimization only enables further enums to be unboxed, no point to run it if enum
    // unboxing is disabled.
    if (!appView.options().enableEnumUnboxing) {
      return;
    }
    // The optimization relies on shrinking and member value propagation to actually clear
    // the anonymous subclasses of EnumLiteMap.
    if (!appView.options().isShrinking()) {
      return;
    }
    internalClearDeadEnumLiteMaps(prunedItemsBuilder);
  }

  private void internalClearDeadEnumLiteMaps(PrunedItems.Builder prunedItemsBuilder) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isDeadEnumLiteMap(clazz)) {
        deadEnumLiteMaps.add(clazz.getType());
        // Clears the EnumLiteMap methods to avoid them being IR processed.
        clazz
            .virtualMethods()
            .forEach(method -> prunedItemsBuilder.addRemovedMethod(method.getReference()));
        clazz.setVirtualMethods(DexEncodedMethod.EMPTY_ARRAY);
      }
    }
  }

  public boolean isDeadEnumLiteMap(DexProgramClass clazz) {
    if (clazz.getInterfaces().contains(references.enumLiteMapType)) {
      DexProgramClass enumLite = computeCorrespondingEnumLite(clazz);
      if (enumLite != null) {
        DexClassAndField field =
            enumLite.lookupClassField(createInternalValueMapField(enumLite.getType()));
        if (field == null) {
          return false;
        }
        if (appView.appInfo().isFieldRead(field)) {
          return false;
        }
        return !appView.appInfo().isFieldWritten(field)
            || appView.appInfo().isStaticFieldWrittenOnlyInEnclosingStaticInitializer(field);
      }
    }
    return false;
  }

  /**
   * Each EnumLiteMap subclass has only two virtual methods findValueByNumber:
   *
   * <ul>
   *   <li>EnumLite findValueByNumber(int)
   *   <li>ConcreteEnumLiteSubType findValueByNumber(int)
   * </ul>
   *
   * <p>The method with the EnumLite return type is the bridge, we extract the concrete EnumLite
   * subtype from the other method return type.
   *
   * <p>We bail out if other virtual methods than the two expected ones are found and return null.
   */
  private DexProgramClass computeCorrespondingEnumLite(DexProgramClass enumLiteMap) {
    if (enumLiteMap.getMethodCollection().numberOfVirtualMethods() != 2) {
      return null;
    }
    DexType enumLiteCandidate = null;
    for (DexEncodedMethod virtualMethod : enumLiteMap.virtualMethods()) {
      if (!matchesFindValueByNumberMethod(virtualMethod.getReference())) {
        return null;
      }
      if (virtualMethod.returnType() == references.enumLiteType) {
        continue;
      }
      if (enumLiteCandidate != null) {
        return null;
      }
      enumLiteCandidate = virtualMethod.returnType();
    }
    if (enumLiteCandidate == null) {
      return null;
    }
    DexProgramClass enumLite = appView.programDefinitionFor(enumLiteCandidate, enumLiteMap);
    if (enumLite != null
        && enumLite.isEnum()
        && enumLite.interfaces.contains(references.enumLiteType)) {
      return enumLite;
    }
    return null;
  }

  private boolean matchesFindValueByNumberMethod(DexMethod method) {
    return method.name == references.findValueByNumberName
        && method.getArity() == 1
        && method.getParameters().values[0] == appView.dexItemFactory().intType;
  }

  public void verifyDeadEnumLiteMapsAreDead() {
    for (DexType deadEnumLiteMap : deadEnumLiteMaps) {
      if (appView.appInfo().definitionForWithoutExistenceAssert(deadEnumLiteMap) != null) {
        throw new CompilationError(
            "EnumLite Proto Shrinker failure: Type "
                + deadEnumLiteMap
                + " was assumed to be dead during optimizations, but it is not.");
      }
    }
  }
}
