// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfo;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.LinkedHashMap;

/**
 * Extracts the ordinal values and any anonymous subtypes for all Enum classes from their static
 * initializer.
 *
 * <p>An Enum class has a field for each value. In the class initializer, each field is initialized
 * to a singleton object that represents the value. This code matches on the corresponding call to
 * the constructor (instance initializer) and extracts the value of the second argument, which is
 * the ordinal and the holder which is the concrete type.
 */
public class EnumValueInfoMapCollector {

  private final AppView<AppInfoWithLiveness> appView;

  private final EnumValueInfoMapCollection.Builder valueInfoMapsBuilder =
      EnumValueInfoMapCollection.builder();

  public EnumValueInfoMapCollector(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public AppInfoWithLiveness run() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      processClasses(clazz);
    }
    EnumValueInfoMapCollection valueInfoMaps = valueInfoMapsBuilder.build();
    if (!valueInfoMaps.isEmpty()) {
      return appView.appInfo().withEnumValueInfoMaps(valueInfoMaps);
    }
    return appView.appInfo();
  }

  private void processClasses(DexProgramClass clazz) {
    // Enum classes are flagged as such. Also, for library classes, the ordinals are not known.
    if (!clazz.accessFlags.isEnum() || clazz.isNotProgramClass() || !clazz.hasClassInitializer()) {
      return;
    }
    ProgramMethod initializer = clazz.getProgramClassInitializer();
    IRCode code = initializer.buildIR(appView);
    LinkedHashMap<DexField, EnumValueInfo> enumValueInfoMap = new LinkedHashMap<>();
    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      if (staticPut.getField().type != clazz.type) {
        continue;
      }
      Instruction newInstance = staticPut.value().definition;
      if (newInstance == null || !newInstance.isNewInstance()) {
        continue;
      }
      Instruction ordinal = null;
      DexType type = null;
      for (Instruction ctorCall : newInstance.outValue().uniqueUsers()) {
        if (!ctorCall.isInvokeDirect()) {
          continue;
        }
        InvokeDirect invoke = ctorCall.asInvokeDirect();
        if (!appView.dexItemFactory().isConstructor(invoke.getInvokedMethod())
            || invoke.arguments().size() < 3) {
          continue;
        }
        ordinal = invoke.arguments().get(2).definition;
        type = invoke.getInvokedMethod().holder;
        break;
      }
      if (ordinal == null || !ordinal.isConstNumber() || type == null) {
        return;
      }

      EnumValueInfo info = new EnumValueInfo(type, ordinal.asConstNumber().getIntValue());
      if (enumValueInfoMap.put(staticPut.getField(), info) != null) {
        return;
      }
    }
    valueInfoMapsBuilder.put(clazz.type, new EnumValueInfoMap(enumValueInfoMap));
  }
}
