// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Set;
import java.util.function.Consumer;

public class EnumUnboxingUtilityClasses {

  // Synthetic classes for utilities specific to the unboxing of a single enum.
  private final ImmutableMap<DexType, LocalEnumUnboxingUtilityClass> localUtilityClasses;

  // Default enum unboxing utility synthetic class used to hold all the shared unboxed enum
  // methods (ordinal(I), equals(II), etc.).
  private final SharedEnumUnboxingUtilityClass sharedUtilityClass;

  private EnumUnboxingUtilityClasses(
      SharedEnumUnboxingUtilityClass sharedUtilityClass,
      ImmutableMap<DexType, LocalEnumUnboxingUtilityClass> localUtilityClasses) {
    this.sharedUtilityClass = sharedUtilityClass;
    this.localUtilityClasses = localUtilityClasses;
  }

  public void forEach(Consumer<? super EnumUnboxingUtilityClass> consumer) {
    localUtilityClasses.values().forEach(consumer);
    consumer.accept(getSharedUtilityClass());
  }

  public LocalEnumUnboxingUtilityClass getLocalUtilityClass(DexProgramClass enumClass) {
    return getLocalUtilityClass(enumClass.getType());
  }

  public LocalEnumUnboxingUtilityClass getLocalUtilityClass(DexType enumType) {
    LocalEnumUnboxingUtilityClass localEnumUnboxingUtilityClass = localUtilityClasses.get(enumType);
    assert localEnumUnboxingUtilityClass != null;
    return localEnumUnboxingUtilityClass;
  }

  public SharedEnumUnboxingUtilityClass getSharedUtilityClass() {
    return sharedUtilityClass;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private ImmutableMap<DexType, LocalEnumUnboxingUtilityClass> localUtilityClasses;
    private SharedEnumUnboxingUtilityClass sharedUtilityClass;

    public Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public Builder synthesizeEnumUnboxingUtilityClasses(
        Set<DexProgramClass> enumsToUnbox,
        EnumDataMap enumDataMap,
        DirectMappedDexApplication.Builder appBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      SharedEnumUnboxingUtilityClass sharedUtilityClass =
          SharedEnumUnboxingUtilityClass.builder(
                  appView, enumDataMap, enumsToUnbox, fieldAccessInfoCollectionModifierBuilder)
              .build(appBuilder);
      ImmutableMap<DexType, LocalEnumUnboxingUtilityClass> localUtilityClasses =
          createLocalUtilityClasses(enumsToUnbox, appBuilder);
      this.localUtilityClasses = localUtilityClasses;
      this.sharedUtilityClass = sharedUtilityClass;
      return this;
    }

    public EnumUnboxingUtilityClasses build() {
      return new EnumUnboxingUtilityClasses(sharedUtilityClass, localUtilityClasses);
    }

    private ImmutableMap<DexType, LocalEnumUnboxingUtilityClass> createLocalUtilityClasses(
        Set<DexProgramClass> enumsToUnbox, DirectMappedDexApplication.Builder appBuilder) {
      ImmutableMap.Builder<DexType, LocalEnumUnboxingUtilityClass> localUtilityClasses =
          ImmutableMap.builder();
      for (DexProgramClass enumToUnbox : enumsToUnbox) {
        localUtilityClasses.put(
            enumToUnbox.getType(),
            LocalEnumUnboxingUtilityClass.builder(appView, enumToUnbox).build(appBuilder));
      }
      return localUtilityClasses.build();
    }

    static DexType getUtilityClassType(
        DexProgramClass context, String suffix, DexItemFactory dexItemFactory) {
      return dexItemFactory.createType(
          DescriptorUtils.getDescriptorFromClassBinaryName(
              DescriptorUtils.getBinaryNameFromDescriptor(context.getType().toDescriptorString())
                  + suffix));
    }
  }
}
