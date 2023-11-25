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
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LensCodeRewriterUtils {

  private final DexDefinitionSupplier definitions;
  private final GraphLens graphLens;
  private final GraphLens codeLens;

  private final Map<DexProto, DexProto> protoFixupCache = new ConcurrentHashMap<>();

  // Map from original call sites to rewritten call sites to lookup call-sites when writing.
  // TODO(b/176885013): This is redundant if we canonicalize call sites.
  private final Map<DexCallSite, DexCallSite> rewrittenCallSiteCache;

  public LensCodeRewriterUtils(AppView<?> appView) {
    this(appView, appView.graphLens(), appView.codeLens());
  }

  public LensCodeRewriterUtils(AppView<?> appView, boolean enableCallSiteCaching) {
    this.definitions = appView;
    this.graphLens = appView.graphLens();
    this.codeLens = appView.codeLens();
    this.rewrittenCallSiteCache = enableCallSiteCaching ? new ConcurrentHashMap<>() : null;
  }

  public LensCodeRewriterUtils(
      DexDefinitionSupplier definitions, GraphLens graphLens, GraphLens codeLens) {
    this.definitions = definitions;
    this.graphLens = graphLens;
    this.codeLens = codeLens;
    this.rewrittenCallSiteCache = null;
  }

  public DexItemFactory dexItemFactory() {
    return definitions.dexItemFactory();
  }

  public DexCallSite rewriteCallSite(DexCallSite callSite, ProgramMethod context) {
    if (rewrittenCallSiteCache == null) {
      return rewriteCallSiteInternal(callSite, context);
    }
    return rewrittenCallSiteCache.computeIfAbsent(
        callSite, ignored -> rewriteCallSiteInternal(callSite, context));
  }

  @SuppressWarnings("ReferenceEquality")
  private DexCallSite rewriteCallSiteInternal(DexCallSite callSite, ProgramMethod context) {
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
    DexString newMethodName = computeNewMethodName(callSite, context, isLambdaMetaFactory);
    if (!newMethodProto.equals(callSite.methodProto)
        || newMethodName != callSite.methodName
        || newBootstrapMethod != callSite.bootstrapMethod
        || !newArgs.equals(callSite.bootstrapArgs)) {
      return dexItemFactory.createCallSite(
          newMethodName, newMethodProto, newBootstrapMethod, newArgs);
    }
    return callSite;
  }

  private DexString computeNewMethodName(
      DexCallSite callSite, ProgramMethod context, boolean isLambdaMetaFactory) {
    if (!isLambdaMetaFactory) {
      return callSite.methodName;
    }
    assert callSite.getBootstrapArgs().size() > 0;
    assert callSite.getBootstrapArgs().get(0).isDexValueMethodType();
    // The targeted method may have been renamed, we need to update the name if that is the case.
    DexMethod method =
        LambdaDescriptor.getMainFunctionalInterfaceMethodReference(
            callSite, definitions.dexItemFactory());
    return graphLens
        .lookupMethod(method, context.getReference(), InvokeType.INTERFACE, codeLens)
        .getReference()
        .getName();
  }

  @SuppressWarnings("ReferenceEquality")
  public DexMethodHandle rewriteDexMethodHandle(
      DexMethodHandle methodHandle, MethodHandleUse use, ProgramMethod context) {
    if (methodHandle.isMethodHandle()) {
      DexMethod invokedMethod = methodHandle.asMethod();
      MethodHandleType oldType = methodHandle.type;
      MethodLookupResult lensLookup =
          graphLens.lookupMethod(
              invokedMethod, context.getReference(), oldType.toInvokeType(), codeLens);
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
        // with a keep rule (see Enqueuer.traceMethodHandle).
        // Note that the member can be repackaged or minified.
        actualTarget =
            definitions
                .dexItemFactory()
                .createMethod(
                    graphLens.lookupType(invokedMethod.holder, codeLens),
                    rewrittenTarget.proto,
                    rewrittenTarget.name);
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
        return definitions
            .dexItemFactory()
            .createMethodHandle(
                newType,
                actualTarget,
                isInterface,
                rewrittenTarget != actualTarget ? rewrittenTarget : null);
      }
    } else {
      DexField field = methodHandle.asField();
      DexField actualField = graphLens.lookupField(field, codeLens);
      if (actualField != field) {
        return definitions
            .dexItemFactory()
            .createMethodHandle(methodHandle.type, actualField, methodHandle.isInterface);
      }
    }
    return methodHandle;
  }

  @SuppressWarnings("ReferenceEquality")
  public List<DexValue> rewriteBootstrapArguments(
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

  @SuppressWarnings("ReferenceEquality")
  private DexValueMethodType rewriteDexMethodType(DexValueMethodType type) {
    DexProto oldProto = type.value;
    DexProto newProto = rewriteProto(oldProto);
    return newProto != oldProto ? new DexValueMethodType(newProto) : type;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexValue rewriteBootstrapArgument(
      DexValue value, MethodHandleUse use, ProgramMethod context) {
    switch (value.getValueKind()) {
      case METHOD_HANDLE:
        return rewriteDexValueMethodHandle(value.asDexValueMethodHandle(), use, context);
      case METHOD_TYPE:
        return rewriteDexMethodType(value.asDexValueMethodType());
      case TYPE:
        DexType oldType = value.asDexValueType().value;
        DexType newType = graphLens.lookupType(oldType, codeLens);
        return newType != oldType ? new DexValueType(newType) : value;
      default:
        return value;
    }
  }

  public DexProto rewriteProto(DexProto proto) {
    return definitions
        .dexItemFactory()
        .applyClassMappingToProto(
            proto, type -> graphLens.lookupType(type, codeLens), protoFixupCache);
  }

  @SuppressWarnings("ReferenceEquality")
  private DexValueMethodHandle rewriteDexValueMethodHandle(
      DexValueMethodHandle methodHandle, MethodHandleUse use, ProgramMethod context) {
    DexMethodHandle oldHandle = methodHandle.value;
    DexMethodHandle newHandle = rewriteDexMethodHandle(oldHandle, use, context);
    return newHandle != oldHandle ? new DexValueMethodHandle(newHandle) : methodHandle;
  }
}
