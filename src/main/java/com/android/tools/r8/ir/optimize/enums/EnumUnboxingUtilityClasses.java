// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.optimize.enums.EnumUnboxingRewriter.createValuesField;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class EnumUnboxingUtilityClasses {

  public static final String ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX =
      "$r8$EnumUnboxingLocalUtility";
  public static final String ENUM_UNBOXING_SHARED_UTILITY_CLASS_SUFFIX =
      "$r8$EnumUnboxingSharedUtility";

  // Synthetic classes for utilities specific to the unboxing of a single enum.
  private final ImmutableMap<DexType, DexProgramClass> localEnumUnboxingUtilityClasses;

  // Default enum unboxing utility synthetic class used to hold all the shared unboxed enum
  // methods (ordinal(I), equals(II), etc.).
  private final DexProgramClass sharedEnumUnboxingUtilityClass;

  private EnumUnboxingUtilityClasses(
      DexProgramClass sharedEnumUnboxingUtilityClass,
      ImmutableMap<DexType, DexProgramClass> localEnumUnboxingUtilityClasses) {
    this.sharedEnumUnboxingUtilityClass = sharedEnumUnboxingUtilityClass;
    this.localEnumUnboxingUtilityClasses = localEnumUnboxingUtilityClasses;
  }

  public DexType getLocalEnumUnboxingUtilityClass(DexProgramClass enumClass) {
    return getLocalEnumUnboxingUtilityClass(enumClass.getType());
  }

  public DexType getLocalEnumUnboxingUtilityClass(DexType enumType) {
    DexProgramClass localEnumUnboxingUtilityClass = localEnumUnboxingUtilityClasses.get(enumType);
    assert localEnumUnboxingUtilityClass != null;
    return localEnumUnboxingUtilityClass.getType();
  }

  public DexProgramClass getSharedEnumUnboxingUtilityClass() {
    return sharedEnumUnboxingUtilityClass;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public static class Builder {

    private final AppView<?> appView;
    private final Map<DexType, DexProgramClass> localEnumUnboxingUtilityClasses =
        new IdentityHashMap<>();
    private DexProgramClass sharedEnumUnboxingUtilityClass;

    public Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public Builder synthesizeEnumUnboxingUtilityClasses(
        Set<DexProgramClass> enumsToUnbox,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      synthesizeLocalUtilityClasses(
          enumsToUnbox, appBuilder, fieldAccessInfoCollectionModifierBuilder);
      synthesizeSharedUtilityClass(enumsToUnbox, appBuilder);
      return this;
    }

    public EnumUnboxingUtilityClasses build() {
      return new EnumUnboxingUtilityClasses(
          sharedEnumUnboxingUtilityClass, ImmutableMap.copyOf(localEnumUnboxingUtilityClasses));
    }

    private void synthesizeLocalUtilityClasses(
        Set<DexProgramClass> enumsToUnbox,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      for (DexProgramClass enumToUnbox : enumsToUnbox) {
        synthesizeLocalUtilityClass(
            enumToUnbox, appBuilder, fieldAccessInfoCollectionModifierBuilder);
      }
    }

    private void synthesizeLocalUtilityClass(
        DexProgramClass enumToUnbox,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      DexType localUtilityClassType = getLocalUtilityClassType(enumToUnbox);
      assert appView.appInfo().definitionForWithoutExistenceAssert(localUtilityClassType) == null;

      // Required fields.
      DexField reference =
          createValuesField(enumToUnbox.getType(), localUtilityClassType, appView.dexItemFactory());
      DexEncodedField staticField =
          new DexEncodedField(reference, FieldAccessFlags.createPublicStaticSynthetic());
      fieldAccessInfoCollectionModifierBuilder
          .recordFieldReadInUnknownContext(reference)
          .recordFieldWriteInUnknownContext(reference);

      DexProgramClass localUtilityClass =
          new DexProgramClass(
              localUtilityClassType,
              null,
              new SynthesizedOrigin("enum unboxing", EnumUnboxer.class),
              ClassAccessFlags.createPublicFinalSynthetic(),
              appView.dexItemFactory().objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              ClassSignature.noSignature(),
              DexAnnotationSet.empty(),
              new DexEncodedField[] {staticField},
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              appView.dexItemFactory().getSkipNameValidationForTesting(),
              DexProgramClass::checksumFromType);
      appBuilder.addSynthesizedClass(localUtilityClass);
      appView.appInfo().addSynthesizedClass(localUtilityClass, enumToUnbox);
      localEnumUnboxingUtilityClasses.put(enumToUnbox.getType(), localUtilityClass);
    }

    private void synthesizeSharedUtilityClass(
        Set<DexProgramClass> enumsToUnbox, DirectMappedDexApplication.Builder appBuilder) {
      DexType type = getSharedUtilityClassType(findDeterministicContextType(enumsToUnbox));
      assert appView.appInfo().definitionForWithoutExistenceAssert(type) == null;

      DexProgramClass syntheticClass =
          new DexProgramClass(
              type,
              null,
              new SynthesizedOrigin("enum unboxing", EnumUnboxer.class),
              ClassAccessFlags.createPublicFinalSynthetic(),
              appView.dexItemFactory().objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              ClassSignature.noSignature(),
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              appView.dexItemFactory().getSkipNameValidationForTesting(),
              DexProgramClass::checksumFromType);
      appBuilder.addSynthesizedClass(syntheticClass);
      appView.appInfo().addSynthesizedClassToBase(syntheticClass, enumsToUnbox);
      sharedEnumUnboxingUtilityClass = syntheticClass;
    }

    private DexProgramClass findDeterministicContextType(Set<DexProgramClass> contexts) {
      DexProgramClass deterministicContext = null;
      for (DexProgramClass context : contexts) {
        if (deterministicContext == null) {
          deterministicContext = context;
        } else if (context.type.compareTo(deterministicContext.type) < 0) {
          deterministicContext = context;
        }
      }
      return deterministicContext;
    }

    private DexType getLocalUtilityClassType(DexProgramClass context) {
      return getUtilityClassType(context, ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX);
    }

    private DexType getSharedUtilityClassType(DexProgramClass context) {
      return getUtilityClassType(context, ENUM_UNBOXING_SHARED_UTILITY_CLASS_SUFFIX);
    }

    private DexType getUtilityClassType(DexProgramClass context, String suffix) {
      return appView
          .dexItemFactory()
          .createType(
              DescriptorUtils.getDescriptorFromClassBinaryName(
                  DescriptorUtils.getBinaryNameFromDescriptor(
                          context.getType().toDescriptorString())
                      + suffix));
    }
  }
}
