// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

/**
 * Utility class for referencing the various keep annotations and their structure.
 *
 * <p>Use of these references avoids poluting the Java namespace with imports of the java
 * annotations which overlap in name with the actual semantic AST types.
 */
public final class KeepConstants {

  public static String getDescriptor(Class<?> clazz) {
    return "L" + clazz.getTypeName().replace('.', '/') + ";";
  }

  public static String getBinaryNameFromClassTypeName(String classTypeName) {
    return classTypeName.replace('.', '/');
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

  public static final class UsesReflection {
    public static final Class<com.android.tools.r8.keepanno.annotations.UsesReflection> CLASS =
        com.android.tools.r8.keepanno.annotations.UsesReflection.class;
    public static final String DESCRIPTOR = getDescriptor(CLASS);
    public static final String description = "description";
    public static final String value = "value";
    public static final String additionalPreconditions = "additionalPreconditions";
  }

  // Implicit hidden item which is "super type" of Condition and Target.
  public static final class Item {
    public static final String classFromBinding = "classFromBinding";
    public static final String memberFromBinding = "memberFromBinding";

    public static final String className = "className";
    public static final String classConstant = "classConstant";

    public static final String extendsClassName = "extendsClassName";
    public static final String extendsClassConstant = "extendsClassConstant";

    public static final String methodName = "methodName";
    public static final String methodReturnType = "methodReturnType";
    public static final String methodParameters = "methodParameters";

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

    public static final String allow = "allow";
    public static final String disallow = "disallow";
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
}
