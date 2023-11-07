// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.references;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Reference provider/factory.
 *
 * <p>The reference class provides a single point for creating and managing reference objects that
 * represent types, methods and fields in a JVM/DEX application. The objects may or may not be
 * interned/shared.
 *
 * <p>No guarantees are made on identity and all references must be compared by {@code equals}.
 */
@KeepForApi
public final class Reference {

  public static PrimitiveReference BOOL = PrimitiveReference.BOOL;
  public static PrimitiveReference BYTE = PrimitiveReference.BYTE;
  public static PrimitiveReference CHAR = PrimitiveReference.CHAR;
  public static PrimitiveReference SHORT = PrimitiveReference.SHORT;
  public static PrimitiveReference INT = PrimitiveReference.INT;
  public static PrimitiveReference FLOAT = PrimitiveReference.FLOAT;
  public static PrimitiveReference LONG = PrimitiveReference.LONG;
  public static PrimitiveReference DOUBLE = PrimitiveReference.DOUBLE;

  private Reference() {
    // Intentionally hidden.
  }

  public static TypeReference returnTypeFromDescriptor(String descriptor) {
    return descriptor.equals("V") ? null : typeFromDescriptor(descriptor);
  }

  public static TypeReference returnTypeFromTypeName(String typename) {
    return typename.equals("void") ? null : typeFromTypeName(typename);
  }

  public static TypeReference typeFromDescriptor(String descriptor) {
    switch (descriptor.charAt(0)) {
      case 'L':
        return classFromDescriptor(descriptor);
      case '[':
        return arrayFromDescriptor(descriptor);
      default:
        return primitiveFromDescriptor(descriptor);
    }
  }

  public static TypeReference typeFromTypeName(String typeName) {
    return typeFromDescriptor(DescriptorUtils.javaTypeToDescriptor(typeName));
  }

  // Internal helper to convert Class<?> for primitive/array types too.
  private static TypeReference typeFromClass(Class<?> clazz) {
    return typeFromDescriptor(DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()));
  }

  public static PrimitiveReference primitiveFromDescriptor(String descriptor) {
    return PrimitiveReference.fromDescriptor(descriptor);
  }

  /** Get a class reference from a JVM descriptor. */
  public static ClassReference classFromDescriptor(String descriptor) {
    return ClassReference.fromDescriptor(descriptor);
  }

  /**
   * Get a class reference from a JVM binary name.
   *
   * <p>See JVM SE 9 Specification, Section 4.2.1. Binary Class and Interface Names.
   */
  public static ClassReference classFromBinaryName(String binaryName) {
    return classFromDescriptor(DescriptorUtils.getDescriptorFromClassBinaryName(binaryName));
  }

  /**
   * Get a class reference from a Java type name.
   *
   * <p>See Class.getTypeName() from Java 1.8.
   */
  public static ClassReference classFromTypeName(String typeName) {
    return classFromDescriptor(DescriptorUtils.javaTypeToDescriptor(typeName));
  }

  /** Get a class reference from a Java java.lang.Class object. */
  public static ClassReference classFromClass(Class<?> clazz) {
    return classFromTypeName(clazz.getTypeName());
  }

  /** Get an array reference from a JVM descriptor. */
  public static ArrayReference arrayFromDescriptor(String descriptor) {
    return ArrayReference.fromDescriptor(descriptor);
  }

  /** Get an array reference from a base type and dimensions. */
  public static ArrayReference array(TypeReference baseType, int dimensions) {
    return ArrayReference.fromBaseType(baseType, dimensions);
  }

  /** Get a method reference from its full reference specification. */
  public static MethodReference method(
      ClassReference holderClass,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType) {
    return new MethodReference(
        holderClass, methodName, ImmutableList.copyOf(formalTypes), returnType);
  }

  /** Get a method reference from a Java reflection executable. */
  public static MethodReference methodFromMethod(Executable executable) {
    if (executable instanceof Constructor<?>) {
      return methodFromMethod((Constructor<?>) executable);
    } else {
      assert executable instanceof Method;
      return methodFromMethod((Method) executable);
    }
  }

  /** Get a method reference from a Java reflection method. */
  public static MethodReference methodFromMethod(Method method) {
    String methodName = method.getName();
    Class<?> holderClass = method.getDeclaringClass();
    Class<?>[] parameterTypes = method.getParameterTypes();
    Class<?> returnType = method.getReturnType();
    ImmutableList.Builder<TypeReference> builder = ImmutableList.builder();
    for (Class<?> parameterType : parameterTypes) {
      builder.add(typeFromClass(parameterType));
    }
    return method(
        classFromClass(holderClass),
        methodName,
        builder.build(),
        returnType == Void.TYPE ? null : typeFromClass(returnType));
  }

  /** Get a method reference from a Java reflection constructor. */
  public static MethodReference methodFromMethod(Constructor<?> method) {
    Class<?> holderClass = method.getDeclaringClass();
    Class<?>[] parameterTypes = method.getParameterTypes();
    ImmutableList.Builder<TypeReference> builder = ImmutableList.builder();
    for (Class<?> parameterType : parameterTypes) {
      builder.add(typeFromClass(parameterType));
    }
    return method(classFromClass(holderClass), "<init>", builder.build(), null);
  }

  /** Get a method reference from class name, method name and signature. */
  public static MethodReference methodFromDescriptor(
      String classDescriptor, String methodName, String methodDescriptor) {
    ImmutableList.Builder<TypeReference> builder = ImmutableList.builder();
    for (String parameterTypeDescriptor :
        DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor)) {
      builder.add(typeFromDescriptor(parameterTypeDescriptor));
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
    return method(
        classFromDescriptor(classDescriptor),
        methodName,
        builder.build(),
        returnTypeDescriptor.equals("V") ? null : typeFromDescriptor(returnTypeDescriptor));
  }

  /** Get a method reference from class reference, method name and signature. */
  public static MethodReference methodFromDescriptor(
      ClassReference classReference, String methodName, String methodDescriptor) {
    ImmutableList.Builder<TypeReference> builder = ImmutableList.builder();
    for (String parameterTypeDescriptor :
        DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor)) {
      builder.add(typeFromDescriptor(parameterTypeDescriptor));
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
    return method(
        classReference,
        methodName,
        builder.build(),
        returnTypeDescriptor.equals("V") ? null : typeFromDescriptor(returnTypeDescriptor));
  }

  public static MethodReference classConstructor(ClassReference type) {
    return method(type, "<clinit>", Collections.emptyList(), null);
  }

  /** Get a field reference from its full reference specification. */
  public static FieldReference field(
      ClassReference holderClass, String fieldName, TypeReference fieldType) {
    return new FieldReference(holderClass, fieldName, fieldType);
  }

  /** Get a field reference from a Java reflection field. */
  public static FieldReference fieldFromField(Field field) {
    Class<?> holderClass = field.getDeclaringClass();
    String fieldName = field.getName();
    Class<?> fieldType = field.getType();
    return field(classFromClass(holderClass), fieldName, typeFromClass(fieldType));
  }

  /** Create a package reference from a string */
  public static PackageReference packageFromString(String packageName) {
    // Note, we rely on equality check for packages and do not canonicalize them.
    return new PackageReference(packageName);
  }

  /** Create a package from a java.lang.Package */
  public static PackageReference packageFromPackage(Package pkg) {
    return new PackageReference(pkg.getName());
  }
}
