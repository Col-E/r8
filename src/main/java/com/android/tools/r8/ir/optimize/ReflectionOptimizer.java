// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.DescriptorUtils.getCanonicalNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getClassNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getSimpleClassNameFromDescriptor;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo.ClassNameComputationOption;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.base.Strings;

public class ReflectionOptimizer {

  public static class ClassNameComputationInfo {
    public enum ClassNameComputationOption {
      NONE,
      NAME,           // getName()
      TYPE_NAME,      // getTypeName()
      CANONICAL_NAME, // getCanonicalName()
      SIMPLE_NAME;    // getSimpleName()

      boolean needsToComputeClassName() {
        return this != NONE;
      }

      boolean needsToRegisterTypeReference() {
        return this == SIMPLE_NAME;
      }
    }

    private static final ClassNameComputationInfo DEFAULT_INSTANCE =
        new ClassNameComputationInfo(ClassNameComputationOption.NONE, 0);

    final ClassNameComputationOption classNameComputationOption;
    final int arrayDepth;

    public ClassNameComputationInfo(
        ClassNameComputationOption classNameComputationOption, int arrayDepth) {
      this.classNameComputationOption = classNameComputationOption;
      this.arrayDepth = arrayDepth;
    }

    public static ClassNameComputationInfo none() {
      return DEFAULT_INSTANCE;
    }

    public boolean needsToComputeClassName() {
      return classNameComputationOption.needsToComputeClassName();
    }

    public boolean needsToRegisterTypeReference() {
      return classNameComputationOption.needsToRegisterTypeReference();
    }
  }

  // Rewrite getClass() call to const-class if the type of the given instance is effectively final.
  public static void rewriteGetClass(AppInfoWithLiveness appInfo, IRCode code) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction current = it.next();
      // Conservatively bail out if the containing block has catch handlers.
      // TODO(b/118509730): unless join of all catch types is ClassNotFoundException ?
      if (current.getBlock().hasCatchHandlers()) {
        continue;
      }
      if (!current.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = current.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      // Class<?> Object#getClass() is final and cannot be overridden.
      if (invokedMethod != appInfo.dexItemFactory.objectMethods.getClass) {
        continue;
      }
      Value in = invoke.getReceiver();
      if (in.hasLocalInfo()) {
        continue;
      }
      TypeLatticeElement inType = in.getTypeLattice();
      // Check the receiver is either class type or array type. Also make sure it is not nullable.
      if (!(inType.isClassType() || inType.isArrayType())
          || inType.isNullable()) {
        continue;
      }
      DexType type = inType.isClassType()
          ? inType.asClassTypeLatticeElement().getClassType()
          : inType.asArrayTypeLatticeElement().getArrayType(appInfo.dexItemFactory);
      DexType baseType = type.toBaseType(appInfo.dexItemFactory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      // Only consider program class, e.g., platform can introduce sub types in different versions.
      DexClass clazz = appInfo.definitionFor(baseType);
      if (clazz == null || !clazz.isProgramClass()) {
        continue;
      }
      // Only consider effectively final class. Exception: new Base().getClass().
      if (!baseType.hasSubtypes()
          || !appInfo.isInstantiatedIndirectly(baseType)
          || (!in.isPhi() && in.definition.isCreatingInstanceOrArray())) {
        // Make sure the target (base) type is visible.
        ConstraintWithTarget constraints =
            ConstraintWithTarget.classIsVisible(code.method.method.getHolder(), baseType, appInfo);
        if (constraints == ConstraintWithTarget.NEVER) {
          continue;
        }
        TypeLatticeElement typeLattice = TypeLatticeElement.classClassType(appInfo);
        Value value = code.createValue(typeLattice, invoke.getLocalInfo());
        ConstClass constClass = new ConstClass(value, type);
        it.replaceCurrentInstruction(constClass);
      }
    }
    assert code.isConsistentSSA();
  }

  public static String computeClassName(
      DexString descriptor, DexClass holder, ClassNameComputationInfo classNameComputationInfo) {
    return computeClassName(
        descriptor.toString(),
        holder,
        classNameComputationInfo.classNameComputationOption,
        classNameComputationInfo.arrayDepth);
  }

  public static String computeClassName(
      String descriptor,
      DexClass holder,
      ClassNameComputationOption classNameComputationOption,
      int arrayDepth) {
    String name;
    switch (classNameComputationOption) {
      case NAME:
        name = getClassNameFromDescriptor(descriptor);
        if (arrayDepth > 0) {
          name = Strings.repeat("[", arrayDepth) + "L" + name + ";";
        }
        break;
      case TYPE_NAME:
        // TODO(b/119426668): desugar Type#getTypeName
        throw new Unreachable("Type#getTypeName not supported yet");
        // name = getClassNameFromDescriptor(descriptor);
        // if (arrayDepth > 0) {
        //   name = name + Strings.repeat("[]", arrayDepth);
        // }
        // break;
      case CANONICAL_NAME:
        name = getCanonicalNameFromDescriptor(descriptor);
        if (arrayDepth > 0) {
          name = name + Strings.repeat("[]", arrayDepth);
        }
        break;
      case SIMPLE_NAME:
        assert holder != null;
        boolean renamed = !descriptor.equals(holder.type.toDescriptorString());
        boolean needsToRetrieveInnerName = holder.isMemberClass() || holder.isLocalClass();
        if (!renamed && needsToRetrieveInnerName) {
          name = holder.getInnerClassAttributeForThisClass().getInnerName().toString();
        } else {
          name = getSimpleClassNameFromDescriptor(descriptor);
        }
        if (arrayDepth > 0) {
          name = name + Strings.repeat("[]", arrayDepth);
        }
        break;
      default:
        throw new Unreachable(
            "Unexpected ClassNameComputationOption: '" + classNameComputationOption + "'");
    }
    return name;
  }


}
