// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.dex.code.DexInvokeCustom;
import com.android.tools.r8.dex.code.DexInvokeCustomRange;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeDirectRange;
import com.android.tools.r8.dex.code.DexInvokeInterface;
import com.android.tools.r8.dex.code.DexInvokeInterfaceRange;
import com.android.tools.r8.dex.code.DexInvokePolymorphic;
import com.android.tools.r8.dex.code.DexInvokePolymorphicRange;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.dex.code.DexInvokeSuper;
import com.android.tools.r8.dex.code.DexInvokeSuperRange;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexInvokeVirtualRange;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import org.objectweb.asm.Opcodes;

public enum InvokeType {
  DIRECT(DexInvokeDirect.OPCODE, DexInvokeDirectRange.OPCODE),
  INTERFACE(DexInvokeInterface.OPCODE, DexInvokeInterfaceRange.OPCODE),
  STATIC(DexInvokeStatic.OPCODE, DexInvokeStaticRange.OPCODE),
  SUPER(DexInvokeSuper.OPCODE, DexInvokeSuperRange.OPCODE),
  VIRTUAL(DexInvokeVirtual.OPCODE, DexInvokeVirtualRange.OPCODE),
  NEW_ARRAY(DexNewArray.OPCODE, Invoke.NO_SUCH_DEX_INSTRUCTION),
  MULTI_NEW_ARRAY(Invoke.NO_SUCH_DEX_INSTRUCTION, Invoke.NO_SUCH_DEX_INSTRUCTION),
  CUSTOM(DexInvokeCustom.OPCODE, DexInvokeCustomRange.OPCODE),
  POLYMORPHIC(DexInvokePolymorphic.OPCODE, DexInvokePolymorphicRange.OPCODE);

  private final int dexOpcode;
  private final int dexOpcodeRange;

  InvokeType(int dexOpcode, int dexOpcodeRange) {
    this.dexOpcode = dexOpcode;
    this.dexOpcodeRange = dexOpcodeRange;
  }

  public static InvokeType fromCfOpcode(
      int opcode, DexMethod invokedMethod, DexClassAndMethod context, AppView<?> appView) {
    return fromCfOpcode(opcode, invokedMethod, context, appView, appView.codeLens());
  }

  @SuppressWarnings("ReferenceEquality")
  public static InvokeType fromCfOpcode(
      int opcode,
      DexMethod invokedMethod,
      DexClassAndMethod context,
      AppView<?> appView,
      GraphLens codeLens) {
    switch (opcode) {
      case org.objectweb.asm.Opcodes.INVOKEINTERFACE:
        return InvokeType.INTERFACE;
      case org.objectweb.asm.Opcodes.INVOKESPECIAL:
        return fromInvokeSpecial(invokedMethod, context, appView, codeLens);
      case org.objectweb.asm.Opcodes.INVOKESTATIC:
        return InvokeType.STATIC;
      case org.objectweb.asm.Opcodes.INVOKEVIRTUAL:
        return appView.dexItemFactory().polymorphicMethods.isPolymorphicInvoke(invokedMethod)
                && !appView.options().shouldDesugarVarHandle()
            ? InvokeType.POLYMORPHIC
            : InvokeType.VIRTUAL;
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public static InvokeType fromInvokeSpecial(
      DexMethod invokedMethod, DexClassAndMethod context, AppView<?> appView, GraphLens codeLens) {
    if (invokedMethod.isInstanceInitializer(appView.dexItemFactory())) {
      return InvokeType.DIRECT;
    }

    GraphLens graphLens = appView.graphLens();
    DexMethod originalContext =
        graphLens.getOriginalMethodSignature(context.getReference(), codeLens);
    if (invokedMethod.getHolderType() != originalContext.getHolderType()) {
      if (appView.options().isGeneratingDex()
          && appView.options().canUseNestBasedAccess()
          && context.getHolder().isInANest()) {
        DexClass holderType = appView.definitionFor(invokedMethod.getHolderType());
        if (holderType != null
            && holderType.isInANest()
            && holderType.isInSameNest(context.getHolder())) {
          // Invoking a private super method within a nest must use invoke-direct. Invoking a
          // non-private super method within a nest must use invoke-super.
          MethodLookupResult lookupResult =
              graphLens.lookupMethod(invokedMethod, context.getReference(), InvokeType.DIRECT);
          DexEncodedMethod definition = holderType.lookupMethod(lookupResult.getReference());
          return definition != null && definition.isPrivate()
              ? InvokeType.DIRECT
              : InvokeType.SUPER;
        }
      }
      return InvokeType.SUPER;
    }

    MethodLookupResult lookupResult =
        graphLens.lookupMethod(invokedMethod, context.getReference(), InvokeType.DIRECT);
    if (lookupResult.getType().isStatic()) {
      // This method has been staticized. The original invoke-type is DIRECT.
      return InvokeType.DIRECT;
    }
    if (lookupResult.getType().isVirtual()) {
      // This method has been publicized. The original invoke-type is DIRECT.
      return InvokeType.DIRECT;
    }

    DexEncodedMethod definition = context.getHolder().lookupMethod(lookupResult.getReference());
    if (definition == null) {
      return InvokeType.SUPER;
    }

    // If the definition was moved to the current context from a super class due to vertical class
    // merging, then this used to be an invoke-super.
    DexType originalHolderOfDefinition =
        graphLens.getOriginalMethodSignature(definition.getReference(), codeLens).getHolderType();
    if (originalHolderOfDefinition != originalContext.getHolderType()) {
      return InvokeType.SUPER;
    }

    boolean originalContextIsInterface =
        context.getHolder().isInterface()
            || (appView.hasVerticallyMergedClasses()
                && appView
                    .getVerticallyMergedClasses()
                    .hasInterfaceBeenMergedIntoSubtype(originalContext.getHolderType()));
    if (originalContextIsInterface) {
      // On interfaces invoke-special should be mapped to invoke-super if the invoke-special
      // instruction is used to target a default interface method.
      if (definition.belongsToVirtualPool()) {
        return InvokeType.SUPER;
      }
    } else {
      // Due to desugaring of invoke-special instructions that target virtual methods, this should
      // never target a virtual method.
      assert definition.isPrivate() || lookupResult.getType().isVirtual();
    }

    return InvokeType.DIRECT;
  }

  public int getCfOpcode() {
    switch (this) {
      case DIRECT:
        return org.objectweb.asm.Opcodes.INVOKESPECIAL;
      case INTERFACE:
        return org.objectweb.asm.Opcodes.INVOKEINTERFACE;
      case POLYMORPHIC:
        return org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
      case STATIC:
        return org.objectweb.asm.Opcodes.INVOKESTATIC;
      case SUPER:
        return org.objectweb.asm.Opcodes.INVOKESPECIAL;
      case VIRTUAL:
        return Opcodes.INVOKEVIRTUAL;
      case NEW_ARRAY:
      case MULTI_NEW_ARRAY:
      default:
        throw new Unreachable();
    }
  }

  public int getDexOpcode() {
    assert dexOpcode >= 0;
    return dexOpcode;
  }

  public int getDexOpcodeRange() {
    assert dexOpcodeRange >= 0;
    return dexOpcodeRange;
  }

  public boolean isDirect() {
    return this == DIRECT;
  }

  public boolean isInterface() {
    return this == INTERFACE;
  }

  public boolean isStatic() {
    return this == STATIC;
  }

  public boolean isSuper() {
    return this == SUPER;
  }

  public boolean isVirtual() {
    return this == VIRTUAL;
  }

  public MethodHandleType toMethodHandle(DexMethod targetMethod) {
    switch (this) {
      case STATIC:
        return MethodHandleType.INVOKE_STATIC;
      case VIRTUAL:
        return MethodHandleType.INVOKE_INSTANCE;
      case DIRECT:
        if (targetMethod.name.toString().equals("<init>")) {
          return MethodHandleType.INVOKE_CONSTRUCTOR;
        } else {
          return MethodHandleType.INVOKE_DIRECT;
        }
      case INTERFACE:
        return MethodHandleType.INVOKE_INTERFACE;
      case SUPER:
        return MethodHandleType.INVOKE_SUPER;
      default:
        throw new Unreachable("Conversion to method handle with unexpected invoke type: " + this);
    }
  }
}
