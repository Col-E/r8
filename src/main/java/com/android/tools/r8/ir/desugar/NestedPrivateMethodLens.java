// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class NestedPrivateMethodLens extends NestedGraphLens {

  // Map from nestHost to nest members including nest hosts
  private final DexType nestConstructorType;
  private final Map<DexField, DexMethod> getFieldMap;
  private final Map<DexField, DexMethod> putFieldMap;
  private final AppView<?> appView;

  NestedPrivateMethodLens(
      AppView<?> appView,
      DexType nestConstructorType,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexMethod> getFieldMap,
      Map<DexField, DexMethod> putFieldMap,
      GraphLens previousLens) {
    super(
        ImmutableMap.of(),
        methodMap,
        ImmutableMap.of(),
        null,
        null,
        previousLens,
        appView.dexItemFactory());
    // No concurrent maps here, we do not want synchronization overhead.
    assert methodMap instanceof IdentityHashMap;
    assert getFieldMap instanceof IdentityHashMap;
    assert putFieldMap instanceof IdentityHashMap;
    this.appView = appView;
    this.nestConstructorType = nestConstructorType;
    this.getFieldMap = getFieldMap;
    this.putFieldMap = putFieldMap;
  }

  private DexMethod lookupFieldForMethod(
      DexField field, DexMethod context, Map<DexField, DexMethod> map) {
    assert context != null;
    DexMethod bridge = map.get(field);
    if (bridge != null && bridge.holder != context.holder) {
      return bridge;
    }
    return null;
  }

  @Override
  public DexMethod lookupGetFieldForMethod(DexField field, DexMethod context) {
    assert getPrevious().lookupGetFieldForMethod(field, context) == null;
    return lookupFieldForMethod(field, context, getFieldMap);
  }

  @Override
  public DexMethod lookupPutFieldForMethod(DexField field, DexMethod context) {
    assert getPrevious().lookupPutFieldForMethod(field, context) == null;
    return lookupFieldForMethod(field, context, putFieldMap);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return methodMap.isEmpty();
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method) {
    assert !methodMap.containsKey(getPrevious().lookupMethod(method));
    return true;
  }

  // This is true because mappings specific to this class can be filled.
  @Override
  protected boolean isLegitimateToHaveEmptyMappings() {
    return true;
  }

  private boolean isConstructorBridge(DexMethod method) {
    DexType[] parameters = method.proto.parameters.values;
    if (parameters.length == 0) {
      return false;
    }
    DexType lastParameterType = parameters[parameters.length - 1];
    return lastParameterType == nestConstructorType;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    if (isConstructorBridge(method)) {
      // TODO (b/132767654): Try to write a test which breaks that assertion.
      assert prototypeChanges.isEmpty();
      // TODO(b/164901008): Fix when the number of arguments overflows.
      return RewrittenPrototypeDescription.none().withExtraUnusedNullParameter();
    }
    return prototypeChanges;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    assert originalMethodSignatures == null;
    DexMethod bridge = methodMap.get(previous.getReference());
    if (bridge == null) {
      return previous;
    }
    assert context != null : "Guaranteed by isContextFreeForMethod";
    if (bridge.holder == context.holder) {
      return previous;
    }
    MethodLookupResult.Builder resultBuilder =
        MethodLookupResult.builder(this).setReference(bridge);
    if (isConstructorBridge(bridge)) {
      resultBuilder
          .setPrototypeChanges(
              internalDescribePrototypeChanges(previous.getPrototypeChanges(), bridge))
          .setType(Type.DIRECT);
    } else {
      resultBuilder.setPrototypeChanges(previous.getPrototypeChanges()).setType(Type.STATIC);
    }
    return resultBuilder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends NestedGraphLens.Builder {

    private Map<DexField, DexMethod> getFieldMap = new IdentityHashMap<>();
    private Map<DexField, DexMethod> putFieldMap = new IdentityHashMap<>();

    public void mapGetField(DexField from, DexMethod to) {
      getFieldMap.put(from, to);
    }

    public void mapPutField(DexField from, DexMethod to) {
      putFieldMap.put(from, to);
    }

    public NestedPrivateMethodLens build(AppView<?> appView, DexType nestConstructorType) {
      assert typeMap.isEmpty();
      assert fieldMap.isEmpty();
      if (getFieldMap.isEmpty() && methodMap.isEmpty() && putFieldMap.isEmpty()) {
        return null;
      }
      return new NestedPrivateMethodLens(
          appView, nestConstructorType, methodMap, getFieldMap, putFieldMap, appView.graphLens());
    }
  }
}
