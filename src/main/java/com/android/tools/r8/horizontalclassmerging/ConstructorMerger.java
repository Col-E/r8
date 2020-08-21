// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ConstructorMerger {
  private final AppView<?> appView;
  private final DexProgramClass target;
  private final Collection<DexEncodedMethod> constructors;
  private final DexItemFactory dexItemFactory;

  ConstructorMerger(
      AppView<?> appView, DexProgramClass target, Collection<DexEncodedMethod> constructors) {
    this.appView = appView;
    this.target = target;
    this.constructors = constructors;

    // Constructors should not be empty and all constructors should have the same prototype.
    assert !constructors.isEmpty();
    assert constructors.stream().map(constructor -> constructor.proto()).distinct().count() == 1;

    this.dexItemFactory = appView.dexItemFactory();
  }

  private DexMethod moveConstructor(DexEncodedMethod constructor) {
    DexMethod method =
        dexItemFactory.createFreshMethodName(
            "constructor",
            constructor.holder(),
            constructor.proto(),
            target.type,
            tryMethod -> target.lookupMethod(tryMethod) == null);

    if (constructor.holder() == target.type) {
      target.removeMethod(constructor.toReference());
    }

    target.addDirectMethod(constructor.toTypeSubstitutedMethod(method));
    return method;
  }

  private DexProto getNewConstructorProto() {
    DexEncodedMethod firstConstructor = constructors.stream().findFirst().get();
    DexProto oldProto = firstConstructor.getProto();

    List<DexType> parameters = new ArrayList<>();
    Collections.addAll(parameters, oldProto.parameters.values);
    parameters.add(dexItemFactory.intType);
    // TODO(b/165783587): add synthesised class to prevent constructor merge conflict
    return dexItemFactory.createProto(oldProto.returnType, parameters);
  }

  private MethodAccessFlags getAccessFlags() {
    // TODO(b/164998929): ensure this behaviour is correct, should probably calculate upper bound
    return MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, true);
  }

  public void merge() {
    Map<DexType, DexMethod> typeConstructors = new IdentityHashMap<>();

    for (DexEncodedMethod constructor : constructors) {
      typeConstructors.put(constructor.holder(), moveConstructor(constructor));
    }

    DexProto newProto = getNewConstructorProto();

    DexMethod newConstructor =
        appView.dexItemFactory().createMethod(target.type, newProto, dexItemFactory.initMethodName);
    SynthesizedCode synthesizedCode =
        new SynthesizedCode(
            callerPosition ->
                new ConstructorEntryPoint(
                    typeConstructors.values(), newConstructor, callerPosition));
    DexEncodedMethod newMethod =
        new DexEncodedMethod(
            newConstructor,
            getAccessFlags(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode);

    target.addDirectMethod(newMethod);
  }
}
