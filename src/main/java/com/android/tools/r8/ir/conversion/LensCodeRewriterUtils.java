// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.ARGUMENT_TO_LAMBDA_METAFACTORY;
import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LensCodeRewriterUtils {

  private final DexDefinitionSupplier definitions;
  private final GraphLens graphLens;

  private final Map<DexProto, DexProto> protoFixupCache = new ConcurrentHashMap<>();

  public LensCodeRewriterUtils(AppView<?> appView) {
    this(appView, appView.graphLens());
  }

  public LensCodeRewriterUtils(DexDefinitionSupplier definitions, GraphLens graphLens) {
    this.definitions = definitions;
    this.graphLens = graphLens;
  }

  public DexCallSite rewriteCallSite(DexCallSite callSite, ProgramMethod context) {
    DexItemFactory dexItemFactory = definitions.dexItemFactory();
    DexProto newMethodProto = rewriteProto(callSite.methodProto);
    DexMethodHandle newBootstrapMethod =
        rewriteDexMethodHandle(
            callSite.bootstrapMethod, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    boolean isLambdaMetaFactory =
        dexItemFactory.isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());
    MethodHandleUse methodHandleUse =
        isLambdaMetaFactory ? ARGUMENT_TO_LAMBDA_METAFACTORY : NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
    List<DexValue> newArgs =
        rewriteBootstrapArguments(callSite.bootstrapArgs, methodHandleUse, context);
    if (!newMethodProto.equals(callSite.methodProto)
        || newBootstrapMethod != callSite.bootstrapMethod
        || !newArgs.equals(callSite.bootstrapArgs)) {
      return dexItemFactory.createCallSite(
          callSite.methodName, newMethodProto, newBootstrapMethod, newArgs);
    }
    return callSite;
  }

  public DexMethodHandle rewriteDexMethodHandle(
      DexMethodHandle methodHandle, MethodHandleUse use, ProgramMethod context) {
    if (methodHandle.isMethodHandle()) {
      DexMethod invokedMethod = methodHandle.asMethod();
      MethodHandleType oldType = methodHandle.type;
      MethodLookupResult lensLookup =
          graphLens.lookupMethod(invokedMethod, context.getReference(), oldType.toInvokeType());
      DexMethod rewrittenTarget = lensLookup.getReference();
      DexMethod actualTarget;
      MethodHandleType newType;
      if (use == ARGUMENT_TO_LAMBDA_METAFACTORY) {
        // Lambda metafactory arguments will be lambda desugared away and therefore cannot flow
        // to a MethodHandle.invokeExact call. We can therefore member-rebind with no issues.
        actualTarget = rewrittenTarget;
        newType = lensLookup.getType().toMethodHandle(actualTarget);
      } else {
        assert use == NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
        // MethodHandles that are not arguments to a lambda metafactory will not be desugared
        // away. Therefore they could flow to a MethodHandle.invokeExact call which means that
        // we cannot member rebind. We therefore keep the receiver and also pin the receiver
        // with a keep rule (see Enqueuer.registerMethodHandle).
        actualTarget =
            definitions
                .dexItemFactory()
                .createMethod(invokedMethod.holder, rewrittenTarget.proto, rewrittenTarget.name);
        newType = oldType;
        if (oldType.isInvokeDirect()) {
          // For an invoke direct, the rewritten target must have the same holder as the original.
          // If the method has changed from private to public we need to use virtual instead of
          // direct.
          assert rewrittenTarget.holder == actualTarget.holder;
          newType = lensLookup.getType().toMethodHandle(actualTarget);
          assert newType == MethodHandleType.INVOKE_DIRECT
              || newType == MethodHandleType.INVOKE_INSTANCE;
        }
      }
      if (newType != oldType || actualTarget != invokedMethod || rewrittenTarget != actualTarget) {
        DexClass holder = definitions.definitionFor(actualTarget.holder, context);
        boolean isInterface = holder != null ? holder.isInterface() : methodHandle.isInterface;
        return new DexMethodHandle(
            newType,
            actualTarget,
            isInterface,
            rewrittenTarget != actualTarget ? rewrittenTarget : null);
      }
    } else {
      DexField field = methodHandle.asField();
      DexField actualField = graphLens.lookupField(field);
      if (actualField != field) {
        return new DexMethodHandle(methodHandle.type, actualField, methodHandle.isInterface);
      }
    }
    return methodHandle;
  }

  private List<DexValue> rewriteBootstrapArguments(
      List<DexValue> bootstrapArgs, MethodHandleUse use, ProgramMethod context) {
    List<DexValue> newBootstrapArgs = null;
    boolean changed = false;
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      DexValue argument = bootstrapArgs.get(i);
      DexValue newArgument = rewriteBootstrapArgument(argument, use, context);
      if (newArgument != argument) {
        if (newBootstrapArgs == null) {
          newBootstrapArgs = new ArrayList<>(bootstrapArgs.subList(0, i));
        }
        newBootstrapArgs.add(newArgument);
        changed = true;
      } else if (newBootstrapArgs != null) {
        newBootstrapArgs.add(newArgument);
      }
    }
    return changed ? newBootstrapArgs : bootstrapArgs;
  }

  private DexValueMethodType rewriteDexMethodType(DexValueMethodType type) {
    DexProto oldProto = type.value;
    DexProto newProto = rewriteProto(oldProto);
    return newProto != oldProto ? new DexValueMethodType(newProto) : type;
  }

  private DexValue rewriteBootstrapArgument(
      DexValue value, MethodHandleUse use, ProgramMethod context) {
    switch (value.getValueKind()) {
      case METHOD_HANDLE:
        return rewriteDexValueMethodHandle(value.asDexValueMethodHandle(), use, context);
      case METHOD_TYPE:
        return rewriteDexMethodType(value.asDexValueMethodType());
      case TYPE:
        DexType oldType = value.asDexValueType().value;
        DexType newType = graphLens.lookupType(oldType);
        return newType != oldType ? new DexValueType(newType) : value;
      default:
        return value;
    }
  }

  public DexProto rewriteProto(DexProto proto) {
    return definitions
        .dexItemFactory()
        .applyClassMappingToProto(proto, graphLens::lookupType, protoFixupCache);
  }

  private DexValueMethodHandle rewriteDexValueMethodHandle(
      DexValueMethodHandle methodHandle, MethodHandleUse use, ProgramMethod context) {
    DexMethodHandle oldHandle = methodHandle.value;
    DexMethodHandle newHandle = rewriteDexMethodHandle(oldHandle, use, context);
    return newHandle != oldHandle ? new DexValueMethodHandle(newHandle) : methodHandle;
  }
}
