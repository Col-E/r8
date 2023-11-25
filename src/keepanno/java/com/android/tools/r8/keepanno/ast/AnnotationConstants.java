// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.keepanno.ast;

/**
 * Utility class for referencing the various keep annotations and their structure.
 *
 * <p>Use of these references avoids polluting the Java namespace with imports of the java
 * annotations which overlap in name with the actual semantic AST types.
 */
public final class AnnotationConstants {
  public static final class Edge {
    public static final String SIMPLE_NAME = "KeepEdge";
    public static final String DESCRIPTOR = "Lcom/android/tools/r8/keepanno/annotations/KeepEdge;";
    public static final String description = "description";
    public static final String bindings = "bindings";
    public static final String preconditions = "preconditions";
    public static final String consequences = "consequences";
  }

  public static final class ForApi {
    public static final String SIMPLE_NAME = "KeepForApi";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepForApi;";
    public static final String description = "description";
    public static final String additionalTargets = "additionalTargets";
    public static final String memberAccess = "memberAccess";
  }

  public static final class UsesReflection {
    public static final String SIMPLE_NAME = "UsesReflection";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/UsesReflection;";
    public static final String description = "description";
    public static final String value = "value";
    public static final String additionalPreconditions = "additionalPreconditions";
  }

  public static final class UsedByReflection {
    public static final String SIMPLE_NAME = "UsedByReflection";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/UsedByReflection;";
    public static final String description = "description";
    public static final String preconditions = "preconditions";
    public static final String additionalTargets = "additionalTargets";
  }

  public static final class UsedByNative {
    public static final String SIMPLE_NAME = "UsedByNative";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/UsedByNative;";
    // Content is the same as UsedByReflection.
  }

  public static final class CheckRemoved {
    public static final String SIMPLE_NAME = "CheckRemoved";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/CheckRemoved;";
  }

  public static final class CheckOptimizedOut {
    public static final String SIMPLE_NAME = "CheckOptimizedOut";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/CheckOptimizedOut;";
  }

  /** Item properties common to binding items, conditions and targets. */
  public static final class Item {
    public static final String classFromBinding = "classFromBinding";
    public static final String memberFromBinding = "memberFromBinding";
    public static final String className = "className";
    public static final String classConstant = "classConstant";
    public static final String instanceOfClassName = "instanceOfClassName";
    public static final String instanceOfClassNameExclusive = "instanceOfClassNameExclusive";
    public static final String instanceOfClassConstant = "instanceOfClassConstant";
    public static final String instanceOfClassConstantExclusive =
        "instanceOfClassConstantExclusive";
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
  }

  public static final class Binding {
    public static final String SIMPLE_NAME = "KeepBinding";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepBinding;";
    public static final String bindingName = "bindingName";
  }

  public static final class Condition {
    public static final String SIMPLE_NAME = "KeepCondition";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepCondition;";
  }

  public static final class Target {
    public static final String SIMPLE_NAME = "KeepTarget";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepTarget;";
    public static final String kind = "kind";
    public static final String allow = "allow";
    public static final String disallow = "disallow";
  }

  public static final class Kind {
    public static final String SIMPLE_NAME = "KeepItemKind";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepItemKind;";
    public static final String ONLY_CLASS = "ONLY_CLASS";
    public static final String ONLY_MEMBERS = "ONLY_MEMBERS";
    public static final String CLASS_AND_MEMBERS = "CLASS_AND_MEMBERS";
  }

  public static final class Option {
    public static final String SIMPLE_NAME = "KeepOption";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/KeepOption;";
    public static final String SHRINKING = "SHRINKING";
    public static final String OPTIMIZATION = "OPTIMIZATION";
    public static final String OBFUSCATION = "OBFUSCATION";
    public static final String ACCESS_MODIFICATION = "ACCESS_MODIFICATION";
    public static final String ANNOTATION_REMOVAL = "ANNOTATION_REMOVAL";
  }

  public static final class MemberAccess {
    public static final String SIMPLE_NAME = "MemberAccessFlags";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/MemberAccessFlags;";
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
    public static final String SIMPLE_NAME = "MethodAccessFlags";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/MethodAccessFlags;";
    public static final String SYNCHRONIZED = "SYNCHRONIZED";
    public static final String BRIDGE = "BRIDGE";
    public static final String NATIVE = "NATIVE";
    public static final String ABSTRACT = "ABSTRACT";
    public static final String STRICT_FP = "STRICT_FP";
  }

  public static final class FieldAccess {
    public static final String SIMPLE_NAME = "FieldAccessFlags";
    public static final String DESCRIPTOR =
        "Lcom/android/tools/r8/keepanno/annotations/FieldAccessFlags;";
    public static final String VOLATILE = "VOLATILE";
    public static final String TRANSIENT = "TRANSIENT";
  }
}
