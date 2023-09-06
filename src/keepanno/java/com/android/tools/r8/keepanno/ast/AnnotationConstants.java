// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepOption;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.MethodAccessFlags;

/**
 * Utility class for referencing the various keep annotations and their structure.
 *
 * <p>Use of these references avoids polluting the Java namespace with imports of the java
 * annotations which overlap in name with the actual semantic AST types.
 */
public final class AnnotationConstants {

  public static String getDescriptor(Class<?> clazz) {
    return getDescriptorFromClassTypeName(clazz.getTypeName());
  }

  public static String getBinaryNameFromClassTypeName(String classTypeName) {
    return classTypeName.replace('.', '/');
  }

  public static String getDescriptorFromClassTypeName(String classTypeName) {
    return "L" + getBinaryNameFromClassTypeName(classTypeName) + ";";
  }

  public static boolean isKeepAnnotation(String descriptor, boolean visible) {
    if (visible) {
      return false;
    }
    return descriptor.equals(Edge.DESCRIPTOR)
        || descriptor.equals(UsesReflection.DESCRIPTOR)
        || descriptor.equals(Condition.DESCRIPTOR)
        || descriptor.equals(Target.DESCRIPTOR);
  }

  public static final class Edge {
    public static final Class<KeepEdge> CLASS = KeepEdge.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
    public static final String bindings = "bindings";
    public static final String preconditions = "preconditions";
    public static final String consequences = "consequences";
  }

  public static final class ForApi {
    public static final Class<KeepForApi> CLASS = KeepForApi.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
    public static final String additionalTargets = "additionalTargets";
    public static final String memberAccess = "memberAccess";
  }

  public static final class UsesReflection {
    public static final Class<com.android.tools.r8.keepanno.annotations.UsesReflection> CLASS =
        com.android.tools.r8.keepanno.annotations.UsesReflection.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
    public static final String value = "value";
    public static final String additionalPreconditions = "additionalPreconditions";
  }

  public static final class UsedByReflection {
    public static final Class<com.android.tools.r8.keepanno.annotations.UsedByReflection> CLASS =
        com.android.tools.r8.keepanno.annotations.UsedByReflection.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
    public static final String preconditions = "preconditions";
    public static final String additionalTargets = "additionalTargets";
    public static final String memberAccess = "memberAccess";
  }

  public static final class UsedByNative {
    public static final Class<com.android.tools.r8.keepanno.annotations.UsedByNative> CLASS =
        com.android.tools.r8.keepanno.annotations.UsedByNative.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    // Content is the same as UsedByReflection.
  }

  public static final class CheckRemoved {
    public static final Class<com.android.tools.r8.keepanno.annotations.CheckRemoved> CLASS =
        com.android.tools.r8.keepanno.annotations.CheckRemoved.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
  }

  public static final class CheckOptimizedOut {
    public static final Class<com.android.tools.r8.keepanno.annotations.CheckOptimizedOut> CLASS =
        com.android.tools.r8.keepanno.annotations.CheckOptimizedOut.class;

    @SuppressWarnings("MutablePublicArray")
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String description = "description";
  }

  // Implicit hidden item which is "super type" of Condition and Target.
  public static final class Item {
    public static final String classFromBinding = "classFromBinding";
    public static final String memberFromBinding = "memberFromBinding";

    public static final String className = "className";
    public static final String classConstant = "classConstant";

    public static final String extendsClassName = "extendsClassName";
    public static final String extendsClassConstant = "extendsClassConstant";

    public static final String memberAccess = "memberAccess";

    public static final String methodAccess = "methodAccess";
    public static final String methodName = "methodName";
    public static final String methodReturnType = "methodReturnType";
    public static final String methodParameters = "methodParameters";

    public static final String fieldAccess = "fieldAccess";
    public static final String fieldName = "fieldName";
    public static final String fieldType = "fieldType";

    // Default values for the optional entries. The defaults should be chosen such that they do
    // not coincide with any actual valid value. E.g., the empty string in place of a name or type.
    // These must be 1:1 with the value defined on the actual annotation definition.
    public static final String classNameDefault = "";
    public static final Class<?> classConstantDefault = Object.class;

    public static final String extendsClassNameDefault = "";
    public static final Class<?> extendsClassConstantDefault = Object.class;

    public static final String methodNameDefaultValue = "";
    public static final String methodReturnTypeDefaultValue = "";

    @SuppressWarnings("MutablePublicArray")
    public static final String[] methodParametersDefaultValue = new String[] {""};

    public static final String fieldNameDefaultValue = "";
    public static final String fieldTypeDefaultValue = "";
  }

  public static final class Binding {
    public static final Class<KeepBinding> CLASS = KeepBinding.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String bindingName = "bindingName";
  }

  public static final class Condition {
    public static final Class<KeepCondition> CLASS = KeepCondition.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
  }

  public static final class Target {
    public static final Class<KeepTarget> CLASS = KeepTarget.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String kind = "kind";
    public static final String allow = "allow";
    public static final String disallow = "disallow";
  }

  public static final class Kind {
    public static final Class<KeepItemKind> CLASS = KeepItemKind.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String DEFAULT = "DEFAULT";
    public static final String ONLY_CLASS = "ONLY_CLASS";
    public static final String ONLY_MEMBERS = "ONLY_MEMBERS";
    public static final String CLASS_AND_MEMBERS = "CLASS_AND_MEMBERS";
  }

  public static final class Option {
    public static final Class<KeepOption> CLASS = KeepOption.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String SHRINKING = "SHRINKING";
    public static final String OBFUSCATION = "OBFUSCATION";
    public static final String OPTIMIZATION = "OPTIMIZATION";
    public static final String ACCESS_MODIFICATION = "ACCESS_MODIFICATION";
    public static final String ANNOTATION_REMOVAL = "ANNOTATION_REMOVAL";
  }

  public static final class MemberAccess {
    public static final Class<MemberAccessFlags> CLASS = MemberAccessFlags.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String NEGATION_PREFIX = "NON_";

    public static final String PUBLIC = "PUBLIC";
    public static final String PROTECTED = "PROTECTED";
    public static final String PACKAGE_PRIVATE = "PACKAGE_PRIVATE";
    public static final String PRIVATE = "PRIVATE";

    public static final String STATIC = "STATIC";
    public static final String FINAL = "FINAL";
    public static final String SYNTHETIC = "SYNTHETIC";
  }

  public static final class MethodAccess {
    public static final Class<MethodAccessFlags> CLASS = MethodAccessFlags.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String SYNCHRONIZED = "SYNCHRONIZED";
    public static final String BRIDGE = "BRIDGE";
    public static final String NATIVE = "NATIVE";
    public static final String ABSTRACT = "ABSTRACT";
    public static final String STRICT_FP = "STRICT_FP";
  }

  public static final class FieldAccess {
    public static final Class<FieldAccessFlags> CLASS = FieldAccessFlags.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);

    public static final String VOLATILE = "VOLATILE";
    public static final String TRANSIENT = "TRANSIENT";
  }
}
