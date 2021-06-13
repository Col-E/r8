// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;

public class LocalEnumUnboxingUtilityClass extends EnumUnboxingUtilityClass {

  private static final String ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX =
      "$r8$EnumUnboxingLocalUtility";

  private final DexProgramClass localUtilityClass;

  public LocalEnumUnboxingUtilityClass(DexProgramClass localUtilityClass) {
    this.localUtilityClass = localUtilityClass;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView, DexProgramClass enumToUnbox) {
    return new Builder(appView, enumToUnbox);
  }

  @Override
  public DexProgramClass getDefinition() {
    return localUtilityClass;
  }

  public DexType getType() {
    return localUtilityClass.getType();
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;
    private final DexProgramClass enumToUnbox;
    private final DexType localUtilityClassType;

    private Builder(AppView<AppInfoWithLiveness> appView, DexProgramClass enumToUnbox) {
      this.appView = appView;
      this.dexItemFactory = appView.dexItemFactory();
      this.enumToUnbox = enumToUnbox;
      this.localUtilityClassType =
          EnumUnboxingUtilityClasses.Builder.getUtilityClassType(
              enumToUnbox, ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX, dexItemFactory);

      assert appView.appInfo().definitionForWithoutExistenceAssert(localUtilityClassType) == null;
    }

    LocalEnumUnboxingUtilityClass build(DirectMappedDexApplication.Builder appBuilder) {
      DexProgramClass clazz = createClass();
      appBuilder.addSynthesizedClass(clazz);
      appView.appInfo().addSynthesizedClass(clazz, enumToUnbox);
      return new LocalEnumUnboxingUtilityClass(clazz);
    }

    private DexProgramClass createClass() {
      return new DexProgramClass(
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
          DexEncodedField.EMPTY_ARRAY,
          DexEncodedField.EMPTY_ARRAY,
          DexEncodedMethod.EMPTY_ARRAY,
          DexEncodedMethod.EMPTY_ARRAY,
          appView.dexItemFactory().getSkipNameValidationForTesting(),
          DexProgramClass::checksumFromType);
    }
  }
}
