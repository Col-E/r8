// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.ARGUMENT_TO_LAMBDA_METAFACTORY;
import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstMethodHandle;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMultiNewArray;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.VerticalClassMerger.VerticallyMergedClasses;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LensCodeRewriter {

  private final AppInfoWithSubtyping appInfo;
  private final GraphLense graphLense;
  private final VerticallyMergedClasses verticallyMergedClasses;
  private final InternalOptions options;

  private final Map<DexProto, DexProto> protoFixupCache = new ConcurrentHashMap<>();

  public LensCodeRewriter(
      AppView<? extends AppInfoWithSubtyping> appView, InternalOptions options) {
    this.appInfo = appView.appInfo();
    this.graphLense = appView.graphLense();
    this.verticallyMergedClasses = appView.verticallyMergedClasses();
    this.options = options;
  }

  private Value makeOutValue(Instruction insn, IRCode code, Set<Value> collector) {
    if (insn.outValue() == null) {
      return null;
    } else {
      Value newValue = code.createValue(insn.outValue().getTypeLattice(), insn.getLocalInfo());
      collector.add(newValue);
      return newValue;
    }
  }

  /**
   * Replace type appearances, invoke targets and field accesses with actual definitions.
   */
  public void rewrite(IRCode code, DexEncodedMethod method) {
    removeUnusedArguments(code, method);
    rewriteInvokeTargets(code, method);
  }

  private void rewriteInvokeTargets(IRCode code, DexEncodedMethod method) {
    Set<Value> newSSAValues = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blocks = code.blocks.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isInvokeCustom()) {
          InvokeCustom invokeCustom = current.asInvokeCustom();
          DexCallSite callSite = invokeCustom.getCallSite();
          DexProto newMethodProto =
              appInfo.dexItemFactory.applyClassMappingToProto(
                  callSite.methodProto, graphLense::lookupType, protoFixupCache);
          DexMethodHandle newBootstrapMethod = rewriteDexMethodHandle(
              callSite.bootstrapMethod, method, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
          boolean isLambdaMetaFactory =
              appInfo.dexItemFactory.isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());
          MethodHandleUse methodHandleUse = isLambdaMetaFactory
              ? ARGUMENT_TO_LAMBDA_METAFACTORY
              : NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
          List<DexValue> newArgs =
              rewriteBootstrapArgs(callSite.bootstrapArgs, method, methodHandleUse);
          if (!newMethodProto.equals(callSite.methodProto)
              || newBootstrapMethod != callSite.bootstrapMethod
              || !newArgs.equals(callSite.bootstrapArgs)) {
            DexCallSite newCallSite =
                appInfo.dexItemFactory.createCallSite(
                    callSite.methodName, newMethodProto, newBootstrapMethod, newArgs);
            InvokeCustom newInvokeCustom = new InvokeCustom(newCallSite, invokeCustom.outValue(),
                invokeCustom.inValues());
            iterator.replaceCurrentInstruction(newInvokeCustom);
          }
        } else if (current.isConstMethodHandle()) {
          DexMethodHandle handle = current.asConstMethodHandle().getValue();
          DexMethodHandle newHandle = rewriteDexMethodHandle(
              handle, method, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
          if (newHandle != handle) {
            ConstMethodHandle newInstruction =
                new ConstMethodHandle(makeOutValue(current, code, newSSAValues), newHandle);
            iterator.replaceCurrentInstruction(newInstruction);
          }
        } else if (current.isInvokeMethod()) {
          InvokeMethod invoke = current.asInvokeMethod();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          DexType invokedHolder = invokedMethod.getHolder();
          if (!invokedHolder.isClassType()) {
            continue;
          }
          if (invoke.isInvokeDirect()) {
            checkInvokeDirect(method.method, invoke.asInvokeDirect());
          }
          GraphLenseLookupResult lenseLookup =
              graphLense.lookupMethod(invokedMethod, method, invoke.getType());
          DexMethod actualTarget = lenseLookup.getMethod();
          Invoke.Type actualInvokeType = lenseLookup.getType();
          if (actualTarget != invokedMethod
              || invoke.getType() != actualInvokeType
              || lenseLookup.hasRemovedArguments()) {
            List<Value> inValues = invoke.inValues();
            if (lenseLookup.hasRemovedArguments()) {
              if (Log.ENABLED) {
                Log.info(
                    getClass(),
                    "Invoked method "
                        + invokedMethod.toSourceString()
                        + " with "
                        + lenseLookup.getRemovedArguments().size()
                        + " arguments removed");
              }
              // Remove removed arguments from the invoke.
              List<Value> newInValues = new ArrayList<>(actualTarget.proto.parameters.size());
              for (int i = 0; i < inValues.size(); i++) {
                if (!lenseLookup.isArgumentRemoved(i)) {
                  newInValues.add(inValues.get(i));
                }
              }
              assert newInValues.size() == actualTarget.proto.parameters.size();
              inValues = newInValues;
            }
            Invoke newInvoke =
                Invoke.create(actualInvokeType, actualTarget, null, invoke.outValue(), inValues);
            iterator.replaceCurrentInstruction(newInvoke);
            // Fix up the return type if needed.
            if (actualTarget.proto.returnType != invokedMethod.proto.returnType
                && newInvoke.outValue() != null) {
              Value newValue = makeOutValue(newInvoke, code, newSSAValues);
              newInvoke.outValue().replaceUsers(newValue);
              CheckCast cast =
                  new CheckCast(
                      newValue,
                      newInvoke.outValue(),
                      graphLense.lookupType(invokedMethod.proto.returnType));
              cast.setPosition(current.getPosition());
              iterator.add(cast);
              // If the current block has catch handlers split the check cast into its own block.
              if (newInvoke.getBlock().hasCatchHandlers()) {
                iterator.previous();
                iterator.split(code, 1, blocks);
              }
            }
          }
        } else if (current.isInstanceGet()) {
          InstanceGet instanceGet = current.asInstanceGet();
          DexField field = instanceGet.getField();
          DexField actualField = graphLense.lookupField(field);
          if (actualField != field) {
            InstanceGet newInstanceGet =
                new InstanceGet(
                    instanceGet.getType(), instanceGet.dest(), instanceGet.object(), actualField);
            iterator.replaceCurrentInstruction(newInstanceGet);
          }
        } else if (current.isInstancePut()) {
          InstancePut instancePut = current.asInstancePut();
          DexField field = instancePut.getField();
          DexField actualField = graphLense.lookupField(field);
          if (actualField != field) {
            InstancePut newInstancePut =
                new InstancePut(
                    instancePut.getType(), actualField, instancePut.object(), instancePut.value());
            iterator.replaceCurrentInstruction(newInstancePut);
          }
        } else if (current.isStaticGet()) {
          StaticGet staticGet = current.asStaticGet();
          DexField field = staticGet.getField();
          DexField actualField = graphLense.lookupField(field);
          if (actualField != field) {
            StaticGet newStaticGet =
                new StaticGet(staticGet.getType(), staticGet.dest(), actualField);
            iterator.replaceCurrentInstruction(newStaticGet);
          }
        } else if (current.isStaticPut()) {
          StaticPut staticPut = current.asStaticPut();
          DexField field = staticPut.getField();
          DexField actualField = graphLense.lookupField(field);
          if (actualField != field) {
            StaticPut newStaticPut =
                new StaticPut(staticPut.getType(), staticPut.inValue(), actualField);
            iterator.replaceCurrentInstruction(newStaticPut);
          }
        } else if (current.isCheckCast()) {
          CheckCast checkCast = current.asCheckCast();
          DexType newType = graphLense.lookupType(checkCast.getType());
          if (newType != checkCast.getType()) {
            CheckCast newCheckCast = new CheckCast(
                makeOutValue(checkCast, code, newSSAValues), checkCast.object(), newType);
            iterator.replaceCurrentInstruction(newCheckCast);
          }
        } else if (current.isConstClass()) {
          ConstClass constClass = current.asConstClass();
          DexType newType = graphLense.lookupType(constClass.getValue());
          if (newType != constClass.getValue()) {
            ConstClass newConstClass = new ConstClass(
                makeOutValue(constClass, code, newSSAValues), newType);
            iterator.replaceCurrentInstruction(newConstClass);
          }
        } else if (current.isInstanceOf()) {
          InstanceOf instanceOf = current.asInstanceOf();
          DexType newType = graphLense.lookupType(instanceOf.type());
          if (newType != instanceOf.type()) {
            InstanceOf newInstanceOf = new InstanceOf(
                makeOutValue(instanceOf, code, newSSAValues), instanceOf.value(), newType);
            iterator.replaceCurrentInstruction(newInstanceOf);
          }
        } else if (current.isInvokeMultiNewArray()) {
          InvokeMultiNewArray multiNewArray = current.asInvokeMultiNewArray();
          DexType newType = graphLense.lookupType(multiNewArray.getArrayType());
          if (newType != multiNewArray.getArrayType()) {
            InvokeMultiNewArray newMultiNewArray =
                new InvokeMultiNewArray(
                    newType,
                    makeOutValue(multiNewArray, code, newSSAValues),
                    multiNewArray.inValues());
            iterator.replaceCurrentInstruction(newMultiNewArray);
          }
        } else if (current.isInvokeNewArray()) {
          InvokeNewArray newArray = current.asInvokeNewArray();
          DexType newType = graphLense.lookupType(newArray.getArrayType());
          if (newType != newArray.getArrayType()) {
            InvokeNewArray newNewArray = new InvokeNewArray(
                newType, makeOutValue(newArray, code, newSSAValues), newArray.inValues());
            iterator.replaceCurrentInstruction(newNewArray);
          }
        } else if (current.isNewArrayEmpty()) {
          NewArrayEmpty newArrayEmpty = current.asNewArrayEmpty();
          DexType newType = graphLense.lookupType(newArrayEmpty.type);
          if (newType != newArrayEmpty.type) {
            NewArrayEmpty newNewArray = new NewArrayEmpty(
                makeOutValue(newArrayEmpty, code, newSSAValues), newArrayEmpty.size(), newType);
            iterator.replaceCurrentInstruction(newNewArray);
          }
        } else if (current.isNewInstance()) {
          NewInstance newInstance= current.asNewInstance();
          DexType newClazz = graphLense.lookupType(newInstance.clazz);
          if (newClazz != newInstance.clazz) {
            NewInstance newNewInstance = new NewInstance(
                newClazz, makeOutValue(newInstance, code, newSSAValues));
            iterator.replaceCurrentInstruction(newNewInstance);
          }
        }
      }
    }
    if (!newSSAValues.isEmpty()) {
      new TypeAnalysis(appInfo, method).widening(newSSAValues);
    }
    assert code.isConsistentSSA();
  }

  // If the given invoke is on the form "invoke-direct A.<init>, v0, ..." and the definition of
  // value v0 is "new-instance v0, B", where B is a subtype of A (see the Art800 and B116282409
  // tests), then fail with a compilation error if A has previously been merged into B.
  //
  // The motivation for this is that the vertical class merger cannot easily recognize the above
  // code pattern, since it runs prior to IR construction. Therefore, we currently allow merging
  // A and B although this will lead to invalid code, because this code pattern does generally
  // not occur in practice (it leads to a verification error on the JVM, but not on Art).
  private void checkInvokeDirect(DexMethod method, InvokeDirect invoke) {
    if (verticallyMergedClasses == null) {
      // No need to check the invocation.
      return;
    }
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.name != appInfo.dexItemFactory.constructorMethodName) {
      // Not a constructor call.
      return;
    }
    if (invoke.arguments().isEmpty()) {
      // The new instance should always be passed to the constructor call, but continue gracefully.
      return;
    }
    Value receiver = invoke.arguments().get(0);
    if (!receiver.isPhi() && receiver.definition.isNewInstance()) {
      NewInstance newInstance = receiver.definition.asNewInstance();
      if (newInstance.clazz != invokedMethod.holder
          && verticallyMergedClasses.hasBeenMergedIntoSubtype(invokedMethod.holder)) {
        // Generated code will not work. Fail with a compilation error.
        throw options.reporter.fatalError(
            String.format(
                "Unable to rewrite `invoke-direct %s.<init>(new %s, ...)` in method `%s` after "
                    + "type `%s` was merged into `%s`. Please add the following rule to your "
                    + "Proguard configuration file: `-keep,allowobfuscation class %s`.",
                invokedMethod.holder.toSourceString(),
                newInstance.clazz,
                method.toSourceString(),
                invokedMethod.holder,
                verticallyMergedClasses.getTargetFor(invokedMethod.holder),
                invokedMethod.holder.toSourceString()));
      }
    }
  }

  private List<DexValue> rewriteBootstrapArgs(
      List<DexValue> bootstrapArgs, DexEncodedMethod method, MethodHandleUse use) {
    List<DexValue> newBoostrapArgs = null;
    boolean changed = false;
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      DexValue argument = bootstrapArgs.get(i);
      DexValue newArgument = null;
      if (argument instanceof DexValueMethodHandle) {
        DexMethodHandle oldHandle = ((DexValueMethodHandle) argument).value;
        DexMethodHandle newHandle =
            rewriteDexMethodHandle(oldHandle, method, use);
        if (newHandle != oldHandle) {
          newArgument = new DexValueMethodHandle(newHandle);
        }
      } else if (argument instanceof DexValueMethodType) {
        DexProto oldProto = ((DexValueMethodType) argument).value;
        DexProto newProto =
            appInfo.dexItemFactory.applyClassMappingToProto(
                oldProto, graphLense::lookupType, protoFixupCache);
        if (newProto != oldProto) {
          newArgument = new DexValueMethodType(newProto);
        }
      } else if (argument instanceof DexValueType) {
        DexType oldType = ((DexValueType) argument).value;
        DexType newType = graphLense.lookupType(oldType);
        if (newType != oldType) {
          newArgument = new DexValueType(newType);
        }
      }
      if (newArgument != null) {
        if (newBoostrapArgs == null) {
          newBoostrapArgs = new ArrayList<>(bootstrapArgs.subList(0, i));
        }
        newBoostrapArgs.add(newArgument);
        changed = true;
      } else if (newBoostrapArgs != null) {
        newBoostrapArgs.add(argument);
      }
    }
    return changed ? newBoostrapArgs : bootstrapArgs;
  }

  private DexMethodHandle rewriteDexMethodHandle(
      DexMethodHandle methodHandle, DexEncodedMethod context, MethodHandleUse use) {
    if (methodHandle.isMethodHandle()) {
      DexMethod invokedMethod = methodHandle.asMethod();
      MethodHandleType oldType = methodHandle.type;
      GraphLenseLookupResult lenseLookup =
          graphLense.lookupMethod(invokedMethod, context, oldType.toInvokeType());
      DexMethod rewrittenTarget = lenseLookup.getMethod();
      DexMethod actualTarget;
      MethodHandleType newType;
      if (use == ARGUMENT_TO_LAMBDA_METAFACTORY) {
        // Lambda metafactory arguments will be lambda desugared away and therefore cannot flow
        // to a MethodHandle.invokeExact call. We can therefore member-rebind with no issues.
        actualTarget = rewrittenTarget;
        newType = lenseLookup.getType().toMethodHandle(actualTarget);
      } else {
        assert use == NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
        // MethodHandles that are not arguments to a lambda metafactory will not be desugared
        // away. Therefore they could flow to a MethodHandle.invokeExact call which means that
        // we cannot member rebind. We therefore keep the receiver and also pin the receiver
        // with a keep rule (see Enqueuer.registerMethodHandle).
        actualTarget =
            appInfo.dexItemFactory.createMethod(
                invokedMethod.holder, rewrittenTarget.proto, rewrittenTarget.name);
        newType = oldType;
        if (oldType.isInvokeDirect()) {
          // For an invoke direct, the rewritten target must have the same holder as the original.
          // If the method has changed from private to public we need to use virtual instead of
          // direct.
          assert rewrittenTarget.holder == actualTarget.holder;
          newType = lenseLookup.getType().toMethodHandle(actualTarget);
          assert newType == MethodHandleType.INVOKE_DIRECT
              || newType == MethodHandleType.INVOKE_INSTANCE;
        }
      }
      if (newType != oldType || actualTarget != invokedMethod || rewrittenTarget != actualTarget) {
        return new DexMethodHandle(
            newType,
            actualTarget,
            rewrittenTarget != actualTarget ? rewrittenTarget : null);
      }
    } else {
      DexField field = methodHandle.asField();
      DexField actualField = graphLense.lookupField(field);
      if (actualField != field) {
        return new DexMethodHandle(methodHandle.type, actualField);
      }
    }
    return methodHandle;
  }

  private void removeUnusedArguments(IRCode code, DexEncodedMethod method) {
    if (!method.isStatic()) {
      return;
    }
    GraphLenseLookupResult lookup = graphLense.lookupMethod(method.method, method, Type.STATIC);
    if (!lookup.hasRemovedArguments()) {
      return;
    }
    int nextRemovedArgumentsIndex = 0;
    int originalArgumentIndex = 0;
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      assert instruction.isArgument();
      if (lookup.getRemovedArguments().getInt(nextRemovedArgumentsIndex) == originalArgumentIndex) {
        assert instruction.outValue().numberOfAllUsers() == 0;
        iterator.remove();
        nextRemovedArgumentsIndex++;
        if (nextRemovedArgumentsIndex == lookup.getRemovedArguments().size()) {
          break;
        }
      }
      originalArgumentIndex++;
    }
    assert nextRemovedArgumentsIndex == lookup.getRemovedArguments().size();
    if (Log.ENABLED) {
      Log.info(
          getClass(),
          "Removed "
              + lookup.getRemovedArguments().size()
              + " arguments from "
              + method.toSourceString());
    }
  }
}
