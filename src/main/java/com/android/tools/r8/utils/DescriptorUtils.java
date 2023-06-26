// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.MODULES_PREFIX;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InvalidDescriptorException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNameMapper;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

public class DescriptorUtils {

  public static final char DESCRIPTOR_PACKAGE_SEPARATOR = '/';
  public static final char JAVA_PACKAGE_SEPARATOR = '.';
  public static final char INNER_CLASS_SEPARATOR = '$';
  private static final Map<String, String> typeNameToLetterMap =
      ImmutableMap.<String, String>builder()
          .put("void", "V")
          .put("boolean", "Z")
          .put("byte", "B")
          .put("short", "S")
          .put("char", "C")
          .put("int", "I")
          .put("long", "J")
          .put("float", "F")
          .put("double", "D")
          .build();

  private static String internalToDescriptor(
      String typeName, boolean shorty, boolean ignorePrimitives) {
    String descriptor = null;
    if (!ignorePrimitives) {
      descriptor = typeNameToLetterMap.get(typeName);
    }
    if (descriptor != null) {
      return descriptor;
    }
    // Must be some array or object type.
    if (shorty) {
      return "L";
    }
    if (typeName.endsWith("[]")) {
      return "[" + internalToDescriptor(
          typeName.substring(0, typeName.length() - 2), shorty, ignorePrimitives);
    }
    // Must be an object type.
    return "L" + typeName.replace(JAVA_PACKAGE_SEPARATOR, DESCRIPTOR_PACKAGE_SEPARATOR) + ";";
  }

  public static String mapTypeName(String typeName, Function<String, String> typeNameMapper) {
    int arrayDimensions = computeArrayDimensionForTypeName(typeName);
    if (arrayDimensions > 0) {
      typeName = typeName.substring(0, typeName.length() - (arrayDimensions * 2));
    }
    String mappedTypeName = typeNameMapper.apply(typeName);
    if (arrayDimensions == 0) {
      return mappedTypeName;
    }
    StringBuilder builder = new StringBuilder(mappedTypeName);
    for (int i = 0; i < arrayDimensions; i++) {
      builder.append("[]");
    }
    return builder.toString();
  }

  private static int computeArrayDimensionForTypeName(String typeName) {
    int arrayDim = 0;
    for (int i = typeName.length() - 2; i > 0; i -= 2) {
      if (typeName.charAt(i) == '[' && typeName.charAt(i + 1) == ']') {
        arrayDim += 1;
      }
    }
    return arrayDim;
  }

  /**
   * Convert a Java type name to a descriptor string.
   *
   * @param typeName the java type name
   * @return the descriptor string
   */
  public static String javaTypeToDescriptor(String typeName) {
    assert typeName.indexOf(DESCRIPTOR_PACKAGE_SEPARATOR) == -1;
    return internalToDescriptor(typeName, false, false);
  }

  public static String javaClassToDescriptor(Class<?> clazz) {
    return javaTypeToDescriptor(clazz.getTypeName());
  }

  /**
   * Convert a Java type name to a descriptor string ignoring primitive types.
   *
   * Ignoring primitives mean that type named like int and long are considered class names, will
   * return Lint; and Llong; respectively instead of I and J. These are not legal Java class names,
   * but valid on the JVM and minification/obfuscation can generate them.
   *
   * @param typeName the java type name
   * @return the descriptor string
   */
  public static String javaTypeToDescriptorIgnorePrimitives(String typeName) {
    assert typeName.indexOf(DESCRIPTOR_PACKAGE_SEPARATOR) == -1;
    return internalToDescriptor(typeName, false, true);
  }

  /**
   * Convert a Java type name to a descriptor string only if the given {@param typeName} is valid.
   *
   * @param typeName the java type name
   * @return the descriptor string if {@param typeName} is not valid or null otherwise
   */
  public static String javaTypeToDescriptorIfValidJavaType(String typeName) {
    if (isValidJavaType(typeName)) {
      return javaTypeToDescriptor(typeName);
    }
    return null;
  }

  /**
   * Produces an array descriptor having the number of dimensions specified and the
   * baseTypeDescriptor as base.
   *
   * @param dimensions number of dimensions
   * @param baseTypeDescriptor the base type
   * @return the descriptor string
   */
  public static String toArrayDescriptor(int dimensions, String baseTypeDescriptor) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dimensions; i++) {
      sb.append('[');
    }
    sb.append(baseTypeDescriptor);
    return sb.toString();
  }

  public static String toBaseDescriptor(String descriptor) {
    int lastArrayStartCharacterIndex = -1;
    while (true) {
      int candidateIndex = lastArrayStartCharacterIndex + 1;
      if (candidateIndex >= descriptor.length() || descriptor.charAt(candidateIndex) != '[') {
        break;
      }
      lastArrayStartCharacterIndex = candidateIndex;
    }
    return lastArrayStartCharacterIndex >= 0
        ? descriptor.substring(lastArrayStartCharacterIndex + 1)
        : descriptor;
  }

  /**
   * Determine the given {@param typeName} is a valid jvms binary name or not (jvms 4.2.1).
   *
   * @param typeName the jvms binary name
   * @return true if and only if the given type name is valid jvms binary name
   */
  public static boolean isValidJavaType(String typeName) {
    if (typeName.length() == 0) {
      return false;
    }
    char last = 0;
    for (int i = 0; i < typeName.length(); i++) {
      char c = typeName.charAt(i);
      if (c == ';' ||
          c == '[' ||
          c == '/') {
        return false;
      }
      if (c == '.' && (i == 0 || last == '.')) {
        return false;
      }
      last = c;
    }
    return true;
  }

  /**
   * Convert a Java type name to a shorty descriptor string.
   *
   * @param typeName the java type name
   * @return the shorty descriptor string
   */
  public static String javaTypeToShorty(String typeName) {
    return internalToDescriptor(typeName, true, false);
  }

  /**
   * Convert a type descriptor to a Java type name.
   *
   * @param descriptor type descriptor
   * @return Java type name
   */
  public static String descriptorToJavaType(String descriptor) {
    return descriptorToJavaType(descriptor, null);
  }

  /**
   * Convert a class type descriptor to an ASM internal name.
   *
   * @param descriptor type descriptor
   * @return Java type name
   */
  public static String descriptorToInternalName(String descriptor) {
    switch (descriptor.charAt(0)) {
      case '[':
        return descriptor;
      case 'L':
        return descriptor.substring(1, descriptor.length() - 1);
      default:
        throw new Unreachable("Not array or class type");
    }
  }

  /**
   * Convert a descriptor to a classifier in Kotlin metadata
   * @param descriptor like "Lorg/foo/bar/Baz$Nested;"
   * @return className "org/foo/bar/Baz.Nested"
   */
  public static String descriptorToKotlinClassifier(String descriptor) {
    final String classifier =
        getBinaryNameFromDescriptor(descriptor)
            .replace(INNER_CLASS_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
    if (descriptor.startsWith("Lj$/")) {
      assert classifier.startsWith("j./");
      return "j$/" + classifier.substring(3);
    }
    return classifier;
  }

  /**
   * Convert a type descriptor to a Java type name. Will also deobfuscate class names if a
   * class mapper is provided.
   *
   * @param descriptor type descriptor
   * @param classNameMapper class name mapper for mapping obfuscated class names
   * @return Java type name
   */
  public static String descriptorToJavaType(String descriptor, ClassNameMapper classNameMapper) {
    char c = descriptor.charAt(0);
    switch (c) {
      case 'L':
        assert descriptor.charAt(descriptor.length() - 1) == ';';
        String clazz = descriptor.substring(1, descriptor.length() - 1)
            .replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
        String originalName =
            classNameMapper == null ? clazz : classNameMapper.deobfuscateClassName(clazz);
        return originalName;
      case '[':
        return descriptorToJavaType(descriptor.substring(1), classNameMapper) + "[]";
      default:
        return primitiveDescriptorToJavaType(c);
    }
  }

  public static boolean isPrimitiveDescriptor(String descriptor) {
    if (descriptor.length() != 1) {
      return false;
    }
    return isPrimitiveType(descriptor.charAt(0));
  }

  public static boolean isPrimitiveType(char c) {
    return c == 'Z' || c == 'B' || c == 'S' || c == 'C' || c == 'I' || c == 'F' || c == 'J'
        || c == 'D';
  }

  public static boolean isVoidDescriptor(String descriptor) {
    return descriptor.length() == 1 && isVoidType(descriptor.charAt(0));
  }

  public static boolean isVoidType(char c) {
    return c == 'V';
  }

  public static boolean isArrayDescriptor(String descriptor) {
    if (descriptor.length() < 2) {
      return false;
    }
    if (descriptor.charAt(0) == '[') {
      return isDescriptor(descriptor.substring(1));
    }
    return false;
  }

  public static boolean isDescriptor(String descriptor) {
    return isClassDescriptor(descriptor)
        || isPrimitiveDescriptor(descriptor)
        || isArrayDescriptor(descriptor);
  }

  public static String primitiveDescriptorToJavaType(char primitive) {
    switch (primitive) {
      case 'V':
        return "void";
      case 'Z':
        return "boolean";
      case 'B':
        return "byte";
      case 'S':
        return "short";
      case 'C':
        return "char";
      case 'I':
        return "int";
      case 'J':
        return "long";
      case 'F':
        return "float";
      case 'D':
        return "double";
      default:
        throw new Unreachable("Unknown type " + primitive);
    }
  }

  public static String primitiveDescriptorToBoxedInternalName(char primitive) {
    switch (primitive) {
      case 'V':
        return "java/lang/Void";
      case 'Z':
        return "java/lang/Boolean";
      case 'B':
        return "java/lang/Byte";
      case 'S':
        return "java/lang/Short";
      case 'C':
        return "java/lang/Character";
      case 'I':
        return "java/lang/Integer";
      case 'J':
        return "java/lang/Long";
      case 'F':
        return "java/lang/Float";
      case 'D':
        return "java/lang/Double";
      default:
        throw new Unreachable("Unknown type " + primitive);
    }
  }

  /**
   * Get unqualified class name from its descriptor.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;" or "La/b/C$D;"
   * @return class name i.e. "Object" or "C$D" (not "D")
   */
  public static String getUnqualifiedClassNameFromDescriptor(String classDescriptor) {
    return getUnqualifiedClassNameFromBinaryName(getClassBinaryNameFromDescriptor(classDescriptor));
  }

  /**
   * Get class name from its descriptor.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @return full class name i.e. "java.lang.Object"
   */
  public static String getClassNameFromDescriptor(String classDescriptor) {
    return getClassBinaryNameFromDescriptor(classDescriptor)
        .replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
  }

  /**
   * Get the simple class name from its descriptor.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @return simple class name i.e. "Object"
   */
  public static String getSimpleClassNameFromDescriptor(String classDescriptor) {
    return classDescriptor.substring(
        getSimpleClassNameIndex(classDescriptor), classDescriptor.length() - 1);
  }

  /**
   * Replace the simple class name from its descriptor with a new simple name.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @param newSimpleName a new simple name e.g. "NewObject"
   * @return updated class descriptor i.e. "Ljava/lang/NewObject;"
   */
  public static String replaceSimpleClassNameInDescriptor(
      String classDescriptor, String newSimpleName) {
    return "L"
        + classDescriptor.substring(1, getSimpleClassNameIndex(classDescriptor))
        + newSimpleName
        + ";";
  }

  /**
   * Finds the index of the simple class name in its descriptor.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @return the index of the simple name i.e. 11.
   */
  private static int getSimpleClassNameIndex(String classDescriptor) {
    return Integer.max(classDescriptor.lastIndexOf("/"), 0) + 1;
  }

  /**
   * Get canonical class name from its descriptor.
   *
   * @param classDescriptor a class descriptor i.e. "La/b/C$D;"
   * @return canonical class name i.e. "a.b.C.D"
   */
  public static String getCanonicalNameFromDescriptor(String classDescriptor) {
    return getClassNameFromDescriptor(classDescriptor)
        .replace(INNER_CLASS_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
  }

  /**
   * Convert class to a binary name.
   *
   * @param clazz a java.lang.Class reference
   * @return class binary name i.e. "java/lang/Object"
   */
  public static String getClassBinaryName(Class<?> clazz) {
    return getBinaryNameFromJavaType(clazz.getTypeName());
  }

  /**
   * Get package java name from a class descriptor.
   *
   * @param descriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @return java package name i.e. "java.lang"
   */
  public static String getPackageNameFromDescriptor(String descriptor) {
    return getPackageNameFromBinaryName(getClassBinaryNameFromDescriptor(descriptor));
  }

  /**
   * Convert class descriptor to a binary name.
   *
   * @param classDescriptor a class descriptor i.e. "Ljava/lang/Object;"
   * @return class binary name i.e. "java/lang/Object"
   */
  public static String getClassBinaryNameFromDescriptor(String classDescriptor) {
    assert isClassDescriptor(classDescriptor) : "Invalid class descriptor "
        + classDescriptor;
    return classDescriptor.substring(1, classDescriptor.length() - 1);
  }

  /**
   * Get package java name from a class type name.
   *
   * @param typeName a class descriptor i.e. "java.lang.Object"
   * @return java package name i.e. "java.lang"
   */
  public static String getPackageNameFromTypeName(String typeName) {
    int packageEndIndex = typeName.lastIndexOf(JAVA_PACKAGE_SEPARATOR);
    return (packageEndIndex < 0) ? "" : typeName.substring(0, packageEndIndex);
  }

  /**
   * Convert package name to a binary name.
   *
   * @param packageName a package name i.e., "java.lang"
   * @return java package name in a binary name format, i.e., java/lang
   */
  public static String getPackageBinaryNameFromJavaType(String packageName) {
    return packageName.replace(JAVA_PACKAGE_SEPARATOR, DESCRIPTOR_PACKAGE_SEPARATOR);
  }

  /**
   * Convert class name to a binary name.
   *
   * @param className a package name i.e., "java.lang.Object"
   * @return java class name in a binary name format, i.e., java/lang/Object
   */
  public static String getBinaryNameFromJavaType(String className) {
    return className.replace(JAVA_PACKAGE_SEPARATOR, DESCRIPTOR_PACKAGE_SEPARATOR);
  }

  public static String getJavaTypeFromBinaryName(String className) {
    return className.replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
  }

  public static String getBinaryNameFromDescriptor(String classDescriptor) {
    assert isClassDescriptor(classDescriptor);
    return classDescriptor.substring(1, classDescriptor.length() - 1);
  }

  /**
   * Convert a class binary name to a descriptor.
   *
   * @param typeBinaryName class binary name i.e. "java/lang/Object"
   * @return a class descriptor i.e. "Ljava/lang/Object;"
   */
  public static String getDescriptorFromClassBinaryName(String typeBinaryName) {
    assert typeBinaryName != null;
    return 'L' + typeBinaryName + ';';
  }

  /**
   * Convert a fully qualified name of a classifier in Kotlin metadata to a descriptor.
   * @param className "org/foo/bar/Baz.Nested"
   * @return a class descriptor like "Lorg/foo/bar/Baz$Nested;"
   */
  public static String getDescriptorFromKotlinClassifier(String className) {
    assert className != null;
    assert !className.contains("[") : className;
    return 'L' + className.replace(JAVA_PACKAGE_SEPARATOR, INNER_CLASS_SEPARATOR) + ';';
  }

  /**
   * Get unqualified class name from its binary name.
   *
   * @param classBinaryName a class binary name i.e. "java/lang/Object" or "a/b/C$Inner"
   * @return class name i.e. "Object" or "C$Inner" (not "Inner")
   *
   * Note that we cannot rely on $ separator in binary name or descriptor because a class, which is
   * not a member or local class, can still contain $ in its name. For the correct retrieval of the
   * simple name of member or local classes, use the inner name in the inner-class attribute (or
   * refer to ReflectionOptimizer#computeClassName as an example).
   */
  public static String getUnqualifiedClassNameFromBinaryName(String classBinaryName) {
    int simpleNameIndex = classBinaryName.lastIndexOf(DESCRIPTOR_PACKAGE_SEPARATOR);
    return (simpleNameIndex < 0) ? classBinaryName : classBinaryName.substring(simpleNameIndex + 1);
  }

  public static String computeInnerClassSeparator(
      DexType outerClass, DexType innerClass, DexString innerName) {
    assert innerClass != null;
    // Filter out non-member classes ahead.
    if (outerClass == null || innerName == null) {
      return String.valueOf(INNER_CLASS_SEPARATOR);
    }
    return computeInnerClassSeparator(
        outerClass.getInternalName(), innerClass.getInternalName(), innerName.toString());
  }

  public static String computeInnerClassSeparator(
      String outerDescriptor, String innerDescriptor, String innerName) {
    assert innerName != null && !innerName.isEmpty();
    // outer-internal<separator>inner-name == inner-internal
    if (outerDescriptor.length() + innerName.length() > innerDescriptor.length()) {
      return null;
    }
    String separator =
        innerDescriptor.substring(
            outerDescriptor.length(), innerDescriptor.length() - innerName.length());
    // Any non-$ separator results in a runtime exception in getCanonicalName.
    if (!separator.startsWith(String.valueOf(INNER_CLASS_SEPARATOR))) {
      return null;
    }
    return separator;
  }

  public static boolean isClassDescriptor(String descriptor) {
    char[] buffer = descriptor.toCharArray();
    int length = buffer.length;
    if (length < 3 || buffer[0] != 'L') {
      return false;
    }

    int pos = 1;
    char ch;
    do {
      // First letter of an Ident (an Ident can't be empty)
      if (pos >= length) {
        return false;
      }

      ch = buffer[pos++];
      if (isInvalidChar(ch) || ch == DESCRIPTOR_PACKAGE_SEPARATOR || ch == ';') {
        return false;
      }

      // Next letters of an Ident
      do {
        if (pos >= length) {
          return false;
        }

        ch = buffer[pos++];
        if (isInvalidChar(ch)) {
          return false;
        }
      } while (ch != DESCRIPTOR_PACKAGE_SEPARATOR && ch != ';');

    } while (ch != ';');

    return pos == length;
  }

  /**
   * Get package java name from a class binary name
   *
   * @param classBinaryName a class binary name i.e. "java/lang/Object"
   * @return java package name i.e. "java.lang"
   */
  public static String getPackageNameFromBinaryName(String classBinaryName) {
    int nameIndex = classBinaryName.lastIndexOf(DESCRIPTOR_PACKAGE_SEPARATOR);
    return (nameIndex < 0) ? "" : classBinaryName.substring(0, nameIndex)
        .replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
  }

  private static boolean isInvalidChar(char ch) {
    switch (ch) {
      case JAVA_PACKAGE_SEPARATOR:
      case '[':
        return true;
      default:
        return false;
    }
  }

  /**
   * Guess class descriptor from location of the class file on the file system
   *
   * @param name Path of the file to convert to the corresponding descriptor
   * @return java class descriptor
   */
  public static String guessTypeDescriptor(Path name) {
    String fileName = name.toString();
    if (File.separatorChar != '/') {
      fileName = fileName.replace(File.separatorChar, '/');
    }
    return guessTypeDescriptor(fileName);
  }

  /**
   * Guess class descriptor from location of the class file. This method assumes that the
   * name uses '/' as the separator. Therefore, this should not be the name of a file
   * on a file system.
   *
   * @param name the location of the class file to convert to descriptor
   * @return java class descriptor
   */
  public static String guessTypeDescriptor(String name) {
    assert name != null;
    assert name.endsWith(CLASS_EXTENSION) :
        "Name " + name + " must have " + CLASS_EXTENSION + " suffix";
    String descriptor = name.substring(0, name.length() - CLASS_EXTENSION.length());
    if (descriptor.indexOf(JAVA_PACKAGE_SEPARATOR) != -1) {
      throw new CompilationError("Unexpected class file name: " + name);
    }
    return 'L' + descriptor + ';';
  }

  public static boolean isValidMethodName(String string) {
    if (string.isEmpty()) {
      return false;
    }
    // According to https://source.android.com/devices/tech/dalvik/dex-format#membername
    // '<' SimpleName '>' should be valid. However, the art verifier only allows <init>
    // and <clinit> which is reasonable.
    if ((string.charAt(0) == '<')
        && (string.equals(Constants.INSTANCE_INITIALIZER_NAME)
            || string.equals(Constants.CLASS_INITIALIZER_NAME))) {
      return true;
    }
    int cp;
    for (int i = 0; i < string.length(); i += Character.charCount(cp)) {
      cp = string.codePointAt(i);
      if (!IdentifierUtils.isRelaxedDexIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isValidFieldName(String string) {
    if (string.isEmpty()) {
      return false;
    }
    int start = 0;
    int end = string.length();
    if (string.charAt(0) == '<') {
      if (string.charAt(end - 1) == '>') {
        start = 1;
        --end;
      } else {
        return false;
      }
    }
    int cp;
    for (int i = start; i < end; i += Character.charCount(cp)) {
      cp = string.codePointAt(i);
      if (!IdentifierUtils.isRelaxedDexIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isValidDescriptor(String descriptor) {
    return isValidArrayDescriptor(descriptor)
        || isValidClassDescriptor(descriptor)
        || isPrimitiveDescriptor(descriptor)
        || isVoidDescriptor(descriptor);
  }

  public static boolean isValidArrayDescriptor(String descriptor) {
    if (descriptor.isEmpty() || descriptor.charAt(0) != '[') {
      return false;
    }
    return isValidDescriptor(toBaseDescriptor(descriptor));
  }

  public static boolean isValidClassDescriptor(String string) {
    if (string.length() < 3
        || string.charAt(0) != 'L'
        || string.charAt(string.length() - 1) != ';') {
      return false;
    }
    if (string.charAt(1) == '/' || string.charAt(string.length() - 2) == '/') {
      return false;
    }
    int cp;
    for (int i = 1; i < string.length() - 1; i += Character.charCount(cp)) {
      cp = string.codePointAt(i);
      if (cp != '/' && !IdentifierUtils.isRelaxedDexIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isValidBinaryName(String binaryName) {
    return isValidJavaType(
        binaryName.replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR));
  }

  /**
   * Computes the inner name from the outer- and inner descriptors. If outer is not a prefix of the
   * inner descriptor null is returned. Do not use this method if the relationship between inner and
   * outer is not reflected in the name.
   *
   * @param outerDescriptor the outer descriptor, such as Lfoo/bar/Baz;
   * @param innerDescriptor the inner descriptor, such as Lfoo/bar/Baz$Qux;
   * @return the inner name or null, i.e. Qux in the example above
   */
  public static String getInnerClassNameFromDescriptor(
      String outerDescriptor, String innerDescriptor) {
    if (innerDescriptor.length() <= outerDescriptor.length()) {
      return null;
    }
    String prefix =
        outerDescriptor.substring(0, outerDescriptor.length() - 1) + INNER_CLASS_SEPARATOR;
    if (innerDescriptor.startsWith(prefix)) {
      return innerDescriptor.substring(prefix.length(), innerDescriptor.length() - 1);
    }
    return null;
  }

  public static String getInnerClassNameFromSimpleName(
      String outerSimpleName, String innerSimpleName) {
    if (innerSimpleName.length() <= outerSimpleName.length()) {
      return null;
    }
    String prefix = outerSimpleName + INNER_CLASS_SEPARATOR;
    if (innerSimpleName.startsWith(prefix)) {
      return innerSimpleName.substring(prefix.length());
    }
    return null;
  }

  public static class ModuleAndDescriptor {
    private final String module;
    private final String descriptor;

    ModuleAndDescriptor(String module, String descriptor) {
      this.module = module;
      this.descriptor = descriptor;
    }

    public String getModule() {
      return module;
    }

    public String getDescriptor() {
      return descriptor;
    }
  }

  /**
   * Guess module and class descriptor from the location of a class file in a jrt file system.
   *
   * @param name the location in a jrt file system of the class file to convert to descriptor
   * @return module and java class descriptor
   */
  public static ModuleAndDescriptor guessJrtModuleAndTypeDescriptor(String name) {
    assert name != null;
    assert name.endsWith(CLASS_EXTENSION)
        : "Name " + name + " must have " + CLASS_EXTENSION + " suffix";
    assert name.startsWith(MODULES_PREFIX)
        : "Name " + name + " must have " + MODULES_PREFIX + " prefix";
    assert name.charAt(MODULES_PREFIX.length()) == '/';
    int moduleNameEnd = name.indexOf('/', MODULES_PREFIX.length() + 1);
    String module = name.substring(MODULES_PREFIX.length() + 1, moduleNameEnd);
    String descriptor = name.substring(moduleNameEnd + 1, name.length() - CLASS_EXTENSION.length());
    if (descriptor.indexOf(JAVA_PACKAGE_SEPARATOR) != -1) {
      throw new CompilationError("Unexpected class file name: " + name);
    }
    return new ModuleAndDescriptor(module, 'L' + descriptor + ';');
  }

  public static String getPathFromDescriptor(String descriptor) {
    // We are quite loose on names here to support testing illegal names, too.
    assert descriptor.startsWith("L");
    assert descriptor.endsWith(";");
    return descriptor.substring(1, descriptor.length() - 1) + ".class";
  }

  public static String getPathFromJavaType(Class<?> clazz) {
    return getPathFromJavaType(clazz.getTypeName());
  }

  public static String getPathFromJavaType(String typeName) {
    assert isValidJavaType(typeName);
    return typeName.replace(JAVA_PACKAGE_SEPARATOR, DESCRIPTOR_PACKAGE_SEPARATOR) + ".class";
  }

  public static String getClassFileName(String classDescriptor) {
    assert classDescriptor != null && isClassDescriptor(classDescriptor);
    return getClassBinaryNameFromDescriptor(classDescriptor) + CLASS_EXTENSION;
  }

  public static String getReturnTypeDescriptor(final String methodDescriptor) {
    assert methodDescriptor.indexOf(')') != -1;
    return methodDescriptor.substring(methodDescriptor.indexOf(')') + 1);
  }

  public static String getShortyDescriptor(String descriptor) {
    if (descriptor.length() == 1) {
      return descriptor;
    }
    assert descriptor.charAt(0) == 'L' || descriptor.charAt(0) == '[';
    return "L";
  }

  public static String[] getArgumentTypeDescriptors(final String methodDescriptor) {
    String[] argDescriptors = new String[getArgumentCount(methodDescriptor)];
    int charIdx = 1;
    char c;
    int argIdx = 0;
    int startType;
    while ((c = methodDescriptor.charAt(charIdx)) != ')') {
      switch (c) {
        case 'V':
          throw new InvalidDescriptorException(methodDescriptor);
        case 'Z':
        case 'C':
        case 'B':
        case 'S':
        case 'I':
        case 'F':
        case 'J':
        case 'D':
          argDescriptors[argIdx++] = Character.toString(c);
          break;
        case '[':
          startType = charIdx;
          while (methodDescriptor.charAt(++charIdx) == '[')
            ;
          if (methodDescriptor.charAt(charIdx) == 'L') {
            while (methodDescriptor.charAt(++charIdx) != ';')
              ;
          }
          argDescriptors[argIdx++] = methodDescriptor.substring(startType, charIdx + 1);
          break;
        case 'L':
          startType = charIdx;
          while (methodDescriptor.charAt(++charIdx) != ';')
            ;
          argDescriptors[argIdx++] = methodDescriptor.substring(startType, charIdx + 1);
          break;
        default:
          throw new InvalidDescriptorException(methodDescriptor);
      }
      charIdx++;
    }
    return argDescriptors;
  }

  public static int getArgumentCount(final String methodDescriptor) {
    int length = methodDescriptor.length();
    int charIdx = 1;
    char c;
    int argCount = 0;
    while (charIdx < length && (c = methodDescriptor.charAt(charIdx++)) != ')') {
      if (c == 'L') {
        while (charIdx < length && methodDescriptor.charAt(charIdx++) != ';')
          ;
        // Check if the inner loop found ';' within the boundary.
        if (charIdx >= length || methodDescriptor.charAt(charIdx - 1) != ';') {
          throw new InvalidDescriptorException(methodDescriptor);
        }
        argCount++;
      } else if (c != '[') {
        argCount++;
      }
    }
    // Check if the outer loop found ')' within the boundary.
    if (charIdx >= length || methodDescriptor.charAt(charIdx - 1) != ')') {
      throw new InvalidDescriptorException(methodDescriptor);
    }
    return argCount;
  }
}
