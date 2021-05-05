// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.optimize.enums.EnumUnboxingRewriter.createValuesField;

import com.android.tools.r8.dex.Constants;
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
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnboxedEnumMemberRelocator {

  public static final String ENUM_UNBOXING_UTILITY_CLASS_SUFFIX = "$r8$EnumUnboxingUtility";

  // Default enum unboxing utility synthetic class used to hold all the shared unboxed enum
  // methods (ordinal(I), equals(II), etc.) and the unboxed enums members which were free to be
  // placed anywhere.
  private final DexType defaultEnumUnboxingUtility;
  // Some unboxed enum members have to be placed in a specific package, in this case, we keep a
  // map from unboxed enum types to synthetic classes, so that all members of unboxed enums in the
  // keys are moved to the corresponding value.
  private final ImmutableMap<DexType, DexType> relocationMap;

  public DexType getDefaultEnumUnboxingUtility() {
    return defaultEnumUnboxingUtility;
  }

  public DexType getNewMemberLocationFor(DexType enumType) {
    return relocationMap.getOrDefault(enumType, defaultEnumUnboxingUtility);
  }

  private UnboxedEnumMemberRelocator(
      DexType defaultEnumUnboxingUtility, ImmutableMap<DexType, DexType> relocationMap) {
    this.defaultEnumUnboxingUtility = defaultEnumUnboxingUtility;
    this.relocationMap = relocationMap;
  }

  public static Builder builder(AppView<?> appView) {
    return new Builder(appView);
  }

  public static class Builder {
    private DexProgramClass defaultEnumUnboxingUtility;
    private Map<DexType, DexType> relocationMap = new IdentityHashMap<>();
    private final AppView<?> appView;

    public Builder(AppView<?> appView) {
      this.appView = appView;
    }

    public Builder synthesizeEnumUnboxingUtilityClasses(
        Set<DexProgramClass> enumsToUnbox,
        ProgramPackageCollection enumsToUnboxWithPackageRequirement,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      Set<DexProgramClass> enumsToUnboxWithoutPackageRequirement =
          SetUtils.newIdentityHashSet(enumsToUnbox);
      enumsToUnboxWithoutPackageRequirement.removeIf(enumsToUnboxWithPackageRequirement::contains);
      defaultEnumUnboxingUtility =
          synthesizeUtilityClass(
              enumsToUnbox,
              enumsToUnboxWithoutPackageRequirement,
              appBuilder,
              fieldAccessInfoCollectionModifierBuilder);
      if (!enumsToUnboxWithPackageRequirement.isEmpty()) {
        synthesizeRelocationMap(
            enumsToUnbox,
            enumsToUnboxWithPackageRequirement,
            appBuilder,
            fieldAccessInfoCollectionModifierBuilder);
      }
      return this;
    }

    public UnboxedEnumMemberRelocator build() {
      return new UnboxedEnumMemberRelocator(
          defaultEnumUnboxingUtility.getType(), ImmutableMap.copyOf(relocationMap));
    }

    private void synthesizeRelocationMap(
        Set<DexProgramClass> contexts,
        ProgramPackageCollection enumsToUnboxWithPackageRequirement,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      for (ProgramPackage programPackage : enumsToUnboxWithPackageRequirement) {
        Set<DexProgramClass> enumsToUnboxInPackage = programPackage.classesInPackage();
        DexProgramClass enumUtilityClass =
            synthesizeUtilityClass(
                contexts,
                enumsToUnboxInPackage,
                appBuilder,
                fieldAccessInfoCollectionModifierBuilder);
        if (enumUtilityClass != defaultEnumUnboxingUtility) {
          for (DexProgramClass enumToUnbox : enumsToUnboxInPackage) {
            assert !relocationMap.containsKey(enumToUnbox.type);
            relocationMap.put(enumToUnbox.type, enumUtilityClass.getType());
          }
        }
      }
    }

    private DexProgramClass synthesizeUtilityClass(
        Set<DexProgramClass> contexts,
        Set<DexProgramClass> relocatedEnums,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      DexProgramClass deterministicContext = findDeterministicContextType(contexts);
      String descriptorString = deterministicContext.getType().toDescriptorString();
      String descriptorPrefix = descriptorString.substring(0, descriptorString.length() - 1);
      String syntheticClassDescriptor = descriptorPrefix + ENUM_UNBOXING_UTILITY_CLASS_SUFFIX + ";";
      DexType type = appView.dexItemFactory().createType(syntheticClassDescriptor);

      // Required fields.
      List<DexEncodedField> staticFields = new ArrayList<>(relocatedEnums.size());
      for (DexProgramClass relocatedEnum : relocatedEnums) {
        DexField reference =
            createValuesField(relocatedEnum.getType(), type, appView.dexItemFactory());
        staticFields.add(
            new DexEncodedField(reference, FieldAccessFlags.createPublicStaticSynthetic()));
        fieldAccessInfoCollectionModifierBuilder
            .recordFieldReadInUnknownContext(reference)
            .recordFieldWriteInUnknownContext(reference);
      }
      staticFields.sort(Comparator.comparing(DexEncodedField::getReference));

      // The defaultEnumUnboxingUtility depends on all unboxable enums, and other synthetic types
      // depend on a subset of the unboxable enums, the deterministicContextType can therefore
      // be found twice, and in that case the same utility class can be used for both.
      if (defaultEnumUnboxingUtility != null && type == defaultEnumUnboxingUtility.getType()) {
        defaultEnumUnboxingUtility.appendStaticFields(staticFields);
        return defaultEnumUnboxingUtility;
      }
      assert appView.appInfo().definitionForWithoutExistenceAssert(type) == null;
      DexProgramClass syntheticClass =
          new DexProgramClass(
              type,
              null,
              new SynthesizedOrigin("enum unboxing", EnumUnboxer.class),
              ClassAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC),
              appView.dexItemFactory().objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              ClassSignature.noSignature(),
              DexAnnotationSet.empty(),
              staticFields.toArray(DexEncodedField.EMPTY_ARRAY),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              appView.dexItemFactory().getSkipNameValidationForTesting(),
              DexProgramClass::checksumFromType);
      appBuilder.addSynthesizedClass(syntheticClass);
      appView.appInfo().addSynthesizedClassToBase(syntheticClass, contexts);
      return syntheticClass;
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
  }
}
