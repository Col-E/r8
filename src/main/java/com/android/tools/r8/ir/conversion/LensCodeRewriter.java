// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

public class LensCodeRewriter {

  private final GraphLense graphLense;
  private final AppInfoWithSubtyping appInfo;

  private final Map<DexProto, DexProto> protoFixupCache = new HashMap<>();

  public LensCodeRewriter(GraphLense graphLense, AppInfoWithSubtyping appInfo) {
    this.graphLense = graphLense;
    this.appInfo = appInfo;
  }

  private Value makeOutValue(Instruction insn, IRCode code) {
    if (insn.outValue() == null) {
      return null;
    } else {
      return code.createValue(insn.outType(), insn.getLocalInfo());
    }
  }

  /**
   * Replace invoke targets and field accesses with actual definitions.
   */
  public void rewrite(IRCode code, DexEncodedMethod method) {
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
          DexMethodHandle newBootstrapMethod =
              rewriteDexMethodHandle(callSite.bootstrapMethod, method);
          List<DexValue> newArgs = callSite.bootstrapArgs.stream().map(
              (arg) -> {
                if (arg instanceof DexValueMethodHandle) {
                  return new DexValueMethodHandle(
                      rewriteDexMethodHandle(((DexValueMethodHandle) arg).value, method));
                }
                return arg;
              })
              .collect(Collectors.toList());

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
        } else if (current.isInvokeMethod()) {
          InvokeMethod invoke = current.asInvokeMethod();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          DexType invokedHolder = invokedMethod.getHolder();
          if (!invokedHolder.isClassType()) {
            continue;
          }
          GraphLenseLookupResult lenseLookup =
              graphLense.lookupMethod(invokedMethod, method, invoke.getType());
          DexMethod actualTarget = lenseLookup.getMethod();
          Invoke.Type invokeType = lenseLookup.getType();
          if (actualTarget != invokedMethod || invoke.getType() != invokeType) {
            Invoke newInvoke = Invoke.create(invokeType, actualTarget, null,
                    invoke.outValue(), invoke.inValues());
            iterator.replaceCurrentInstruction(newInvoke);
            // Fix up the return type if needed.
            if (actualTarget.proto.returnType != invokedMethod.proto.returnType
                && newInvoke.outValue() != null) {
              Value newValue = code.createValue(newInvoke.outType(), invoke.getLocalInfo());
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
            CheckCast newCheckCast =
                new CheckCast(makeOutValue(checkCast, code), checkCast.object(), newType);
            iterator.replaceCurrentInstruction(newCheckCast);
          }
        } else if (current.isConstClass()) {
          ConstClass constClass = current.asConstClass();
          DexType newType = graphLense.lookupType(constClass.getValue());
          if (newType != constClass.getValue()) {
            ConstClass newConstClass = new ConstClass(makeOutValue(constClass, code), newType);
            iterator.replaceCurrentInstruction(newConstClass);
          }
        } else if (current.isInstanceOf()) {
          InstanceOf instanceOf = current.asInstanceOf();
          DexType newType = graphLense.lookupType(instanceOf.type());
          if (newType != instanceOf.type()) {
            InstanceOf newInstanceOf = new InstanceOf(makeOutValue(instanceOf, code),
                instanceOf.value(), newType);
            iterator.replaceCurrentInstruction(newInstanceOf);
          }
        } else if (current.isInvokeNewArray()) {
          InvokeNewArray newArray = current.asInvokeNewArray();
          DexType newType = graphLense.lookupType(newArray.getArrayType());
          if (newType != newArray.getArrayType()) {
            InvokeNewArray newNewArray = new InvokeNewArray(newType, makeOutValue(newArray, code),
                newArray.inValues());
            iterator.replaceCurrentInstruction(newNewArray);
          }
        } else if (current.isNewArrayEmpty()) {
          NewArrayEmpty newArrayEmpty = current.asNewArrayEmpty();
          DexType newType = graphLense.lookupType(newArrayEmpty.type);
          if (newType != newArrayEmpty.type) {
            NewArrayEmpty newNewArray = new NewArrayEmpty(makeOutValue(newArrayEmpty, code),
                newArrayEmpty.size(), newType);
            iterator.replaceCurrentInstruction(newNewArray);
          }
        } else if (current.isNewInstance()) {
            NewInstance newInstance= current.asNewInstance();
          DexType newClazz = graphLense.lookupType(newInstance.clazz);
            if (newClazz != newInstance.clazz) {
              NewInstance newNewInstance =
                  new NewInstance(newClazz, makeOutValue(newInstance, code));
              iterator.replaceCurrentInstruction(newNewInstance);
            }
          }
      }
    }
    assert code.isConsistentSSA();
  }

  private DexMethodHandle rewriteDexMethodHandle(
      DexMethodHandle methodHandle, DexEncodedMethod context) {
    if (methodHandle.isMethodHandle()) {
      DexMethod invokedMethod = methodHandle.asMethod();
      MethodHandleType oldType = methodHandle.type;
      GraphLenseLookupResult lenseLookup =
          graphLense.lookupMethod(invokedMethod, context, oldType.toInvokeType());
      DexMethod actualTarget = lenseLookup.getMethod();
      MethodHandleType newType = lenseLookup.getType().toMethodHandle(actualTarget);
      if (actualTarget != invokedMethod) {
        DexClass clazz = appInfo.definitionFor(actualTarget.holder);
        if (clazz != null && (oldType.isInvokeInterface() || oldType.isInvokeInstance())) {
          newType =
              lenseLookup.getType() == Type.INTERFACE
                  ? MethodHandleType.INVOKE_INTERFACE
                  : MethodHandleType.INVOKE_INSTANCE;
        }
        return new DexMethodHandle(newType, actualTarget);
      }
      if (oldType != newType) {
        return new DexMethodHandle(newType, actualTarget);
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
}
