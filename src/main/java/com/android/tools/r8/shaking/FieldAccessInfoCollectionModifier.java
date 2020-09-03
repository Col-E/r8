// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class FieldAccessInfoCollectionModifier {

  static class FieldReferences {
    final List<DexMethod> writeContexts = new ArrayList<>();
    final List<DexMethod> readContexts = new ArrayList<>();

    void fixUpMethods(List<DexMethod> methods, Function<DexMethod, DexMethod> fixUpMethod) {
      for (int i = 0; i < methods.size(); i++) {
        DexMethod method = methods.get(i);
        DexMethod newMethod = fixUpMethod.apply(method);
        if (method != newMethod) {
          methods.set(i, newMethod);
        }
      }
    }

    void fixUp(Function<DexMethod, DexMethod> fixUpMethod) {
      fixUpMethods(writeContexts, fixUpMethod);
      fixUpMethods(readContexts, fixUpMethod);
    }
  }

  final Map<DexField, FieldReferences> newFieldAccesses;

  FieldAccessInfoCollectionModifier(Map<DexField, FieldReferences> newFieldAccesses) {
    this.newFieldAccesses = newFieldAccesses;
  }

  void forEachFieldAccess(
      AppView<?> appView,
      Collection<DexMethod> methods,
      DexField field,
      BiConsumer<DexField, ProgramMethod> record) {
    for (DexMethod method : methods) {
      ProgramMethod programMethod =
          appView.definitionFor(method.holder).asProgramClass().lookupProgramMethod(method);
      record.accept(field, programMethod);
    }
  }

  public void modify(AppView<AppInfoWithLiveness> appView) {
    FieldAccessInfoCollectionImpl impl = appView.appInfo().getMutableFieldAccessInfoCollection();
    newFieldAccesses.forEach(
        (field, info) -> {
          FieldAccessInfoImpl fieldAccessInfo = new FieldAccessInfoImpl(field);
          forEachFieldAccess(appView, info.readContexts, field, fieldAccessInfo::recordRead);
          forEachFieldAccess(appView, info.writeContexts, field, fieldAccessInfo::recordWrite);
          impl.extend(field, fieldAccessInfo);
        });
  }

  public static class Builder {
    final Map<DexField, FieldReferences> newFieldAccesses = new IdentityHashMap<>();

    public Builder() {}

    public FieldAccessInfoCollectionModifier build(Function<DexMethod, DexMethod> fixupMethod) {
      for (FieldReferences fieldReference : newFieldAccesses.values()) {
        fieldReference.fixUp(fixupMethod);
      }
      return new FieldAccessInfoCollectionModifier(newFieldAccesses);
    }

    FieldReferences getFieldReferences(DexField field) {
      return newFieldAccesses.computeIfAbsent(field, ignore -> new FieldReferences());
    }

    public void fieldReadByMethod(DexField field, DexMethod method) {
      getFieldReferences(field).readContexts.add(method);
    }

    public void fieldWrittenByMethod(DexField field, DexMethod method) {
      getFieldReferences(field).writeContexts.add(method);
    }
  }
}
