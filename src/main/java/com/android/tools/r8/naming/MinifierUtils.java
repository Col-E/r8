// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;

public class MinifierUtils {

  public static SubtypingInfo createSubtypingInfo(AppView<AppInfoWithLiveness> appView) {
    Set<DexClass> classesToBuildSubtypeInformationFor =
        SetUtils.newIdentityHashSet(appView.app().classes());
    appView
        .appInfo()
        .getObjectAllocationInfoCollection()
        .forEachInstantiatedLambdaInterfaces(
            type -> {
              DexClass lambdaInterface = appView.contextIndependentDefinitionFor(type);
              if (lambdaInterface != null) {
                classesToBuildSubtypeInformationFor.add(lambdaInterface);
              }
            });
    appView.appInfo().forEachReferencedClasspathClass(classesToBuildSubtypeInformationFor::add);
    return SubtypingInfo.create(classesToBuildSubtypeInformationFor, appView);
  }
}
