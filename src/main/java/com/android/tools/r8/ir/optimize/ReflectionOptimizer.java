// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.DescriptorUtils.getCanonicalNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getClassNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getSimpleClassNameFromDescriptor;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo.ClassNameComputationOption;
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
