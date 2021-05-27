// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.InstanceInitializerMerger.Builder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InstanceInitializerMergerCollection {

  private final List<InstanceInitializerMerger> instanceInitializerMergers;

  private InstanceInitializerMergerCollection(
      List<InstanceInitializerMerger> instanceInitializerMergers) {
    this.instanceInitializerMergers = instanceInitializerMergers;
  }

  public static InstanceInitializerMergerCollection create(
      AppView<? extends AppInfoWithClassHierarchy> appView, MergeGroup group, Mode mode) {
    List<InstanceInitializerMerger> instanceInitializerMergers = new ArrayList<>();
    if (appView.options().horizontalClassMergerOptions().isConstructorMergingEnabled()) {
      Map<DexProto, Builder> buildersByProto = new LinkedHashMap<>();
      group.forEach(
          clazz ->
              clazz.forEachProgramDirectMethodMatching(
                  DexEncodedMethod::isInstanceInitializer,
                  method ->
                      buildersByProto
                          .computeIfAbsent(
                              method.getDefinition().getProto(),
                              ignore -> new InstanceInitializerMerger.Builder(appView, mode))
                          .add(method)));
      for (InstanceInitializerMerger.Builder builder : buildersByProto.values()) {
        instanceInitializerMergers.addAll(builder.build(group));
      }
    } else {
      group.forEach(
          clazz ->
              clazz.forEachProgramDirectMethodMatching(
                  DexEncodedMethod::isInstanceInitializer,
                  method ->
                      instanceInitializerMergers.addAll(
                          new InstanceInitializerMerger.Builder(appView, mode)
                              .add(method)
                              .build(group))));
    }

    // Try and merge the constructors with the most arguments first, to avoid using synthetic
    // arguments if possible.
    instanceInitializerMergers.sort(
        Comparator.comparing(InstanceInitializerMerger::getArity).reversed());
    return new InstanceInitializerMergerCollection(instanceInitializerMergers);
  }

  public void forEach(Consumer<InstanceInitializerMerger> consumer) {
    instanceInitializerMergers.forEach(consumer);
  }
}
