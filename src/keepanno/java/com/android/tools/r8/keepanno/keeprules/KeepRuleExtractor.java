// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepExtendsPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Extract out a sequence of Proguard keep rules that give a conservative over-approximation. */
public class KeepRuleExtractor {

  private final Consumer<String> ruleConsumer;

  public KeepRuleExtractor(Consumer<String> ruleConsumer) {
    this.ruleConsumer = ruleConsumer;
  }

  public void extract(KeepEdge edge) {
    Collection<PgRule> rules = KeepEdgeSplitter.split(edge);
    StringBuilder builder = new StringBuilder();
    for (PgRule rule : rules) {
      rule.printRule(builder);
      builder.append("\n");
    }
    ruleConsumer.accept(builder.toString());
  }

  public static void printHeader(StringBuilder builder, KeepEdgeMetaInfo metaInfo) {
    if (metaInfo.hasContext()) {
      builder.append("# context: ").append(metaInfo.getContextDescriptorString()).append('\n');
    }
    if (metaInfo.hasDescription()) {
      String escapedDescription = escapeLineBreaks(metaInfo.getDescriptionString());
      builder.append("# description: ").append(escapedDescription).append('\n');
    }
  }

  public static String escapeChar(char c) {
    if (c == '\n') {
      return "\\n";
    }
    if (c == '\r') {
      return "\\r";
    }
    return null;
  }

  public static String escapeLineBreaks(String string) {
    char[] charArray = string.toCharArray();
    for (int i = 0; i < charArray.length; i++) {
      // We don't expect escape chars, so wait with constructing a new string until found.
      if (escapeChar(charArray[i]) != null) {
        StringBuilder builder = new StringBuilder(string.substring(0, i));
        for (int j = i; j < charArray.length; j++) {
          char c = charArray[j];
          String escaped = escapeChar(c);
          if (escaped != null) {
            builder.append(escaped);
          } else {
            builder.append(c);
          }
        }
        return builder.toString();
      }
    }
    return string;
  }

  public static void printKeepOptions(StringBuilder builder, KeepOptions options) {
    for (KeepOption option : KeepOption.values()) {
      if (options.isAllowed(option)) {
        builder.append(",allow").append(getOptionString(option));
      }
    }
  }

  public static StringBuilder printClassHeader(
      StringBuilder builder,
      KeepItemPattern classPattern,
      BiConsumer<StringBuilder, KeepClassReference> printClassReference) {
    builder.append("class ");
    printClassReference.accept(builder, classPattern.getClassReference());
    KeepExtendsPattern extendsPattern = classPattern.getExtendsPattern();
    if (!extendsPattern.isAny()) {
      builder.append(" extends ");
      printClassName(builder, extendsPattern.asClassNamePattern());
    }
    return builder;
  }

  public static StringBuilder printMemberClause(StringBuilder builder, KeepMemberPattern member) {
    if (member.isAll()) {
      return builder.append("*;");
    }
    if (member.isMethod()) {
      return printMethod(builder, member.asMethod());
    }
    if (member.isField()) {
      return printField(builder, member.asField());
    }
    throw new Unimplemented();
  }

  private static StringBuilder printField(StringBuilder builder, KeepFieldPattern fieldPattern) {
    if (fieldPattern.isAnyField()) {
      return builder.append("<fields>;");
    }
    printAccess(builder, " ", fieldPattern.getAccessPattern());
    printType(builder, fieldPattern.getTypePattern().asType());
    builder.append(' ');
    printFieldName(builder, fieldPattern.getNamePattern());
    return builder.append(';');
  }

  private static StringBuilder printMethod(StringBuilder builder, KeepMethodPattern methodPattern) {
    if (methodPattern.isAnyMethod()) {
      return builder.append("<methods>;");
    }
    printAccess(builder, " ", methodPattern.getAccessPattern());
    printReturnType(builder, methodPattern.getReturnTypePattern());
    builder.append(' ');
    printMethodName(builder, methodPattern.getNamePattern());
    printParameters(builder, methodPattern.getParametersPattern());
    return builder.append(';');
  }

  private static StringBuilder printParameters(
      StringBuilder builder, KeepMethodParametersPattern parametersPattern) {
    if (parametersPattern.isAny()) {
      return builder.append("(...)");
    }
    return builder
        .append('(')
        .append(
            parametersPattern.asList().stream()
                .map(KeepRuleExtractor::getTypePatternString)
                .collect(Collectors.joining(", ")))
        .append(')');
  }

  private static StringBuilder printFieldName(
      StringBuilder builder, KeepFieldNamePattern namePattern) {
    return namePattern.isAny()
        ? builder.append("*")
        : builder.append(namePattern.asExact().getName());
  }

  private static StringBuilder printMethodName(
      StringBuilder builder, KeepMethodNamePattern namePattern) {
    return namePattern.isAny()
        ? builder.append("*")
        : builder.append(namePattern.asExact().getName());
  }

  private static StringBuilder printReturnType(
      StringBuilder builder, KeepMethodReturnTypePattern returnTypePattern) {
    if (returnTypePattern.isVoid()) {
      return builder.append("void");
    }
    return printType(builder, returnTypePattern.asType());
  }

  private static StringBuilder printType(StringBuilder builder, KeepTypePattern typePattern) {
    return builder.append(getTypePatternString(typePattern));
  }

  private static StringBuilder printAccess(
      StringBuilder builder, String indent, KeepMethodAccessPattern accessPattern) {
    if (accessPattern.isAny()) {
      // No text will match any access pattern.
      // Don't print the indent in this case.
      return builder;
    }
    throw new Unimplemented();
  }

  private static StringBuilder printAccess(
      StringBuilder builder, String indent, KeepFieldAccessPattern accessPattern) {
    if (accessPattern.isAny()) {
      // No text will match any access pattern.
      // Don't print the indent in this case.
      return builder;
    }
    throw new Unimplemented();
  }

  public static StringBuilder printClassName(
      StringBuilder builder, KeepQualifiedClassNamePattern classNamePattern) {
    if (classNamePattern.isAny()) {
      return builder.append('*');
    }
    printPackagePrefix(builder, classNamePattern.getPackagePattern());
    return printSimpleClassName(builder, classNamePattern.getNamePattern());
  }

  private static StringBuilder printPackagePrefix(
      StringBuilder builder, KeepPackagePattern packagePattern) {
    if (packagePattern.isAny()) {
      return builder.append("**.");
    }
    if (packagePattern.isTop()) {
      return builder;
    }
    assert packagePattern.isExact();
    return builder.append(packagePattern.getExactPackageAsString()).append('.');
  }

  private static StringBuilder printSimpleClassName(
      StringBuilder builder, KeepUnqualfiedClassNamePattern namePattern) {
    if (namePattern.isAny()) {
      return builder.append('*');
    }
    assert namePattern.isExact();
    return builder.append(namePattern.asExact().getExactNameAsString());
  }

  private static String getOptionString(KeepOption option) {
    switch (option) {
      case SHRINKING:
        return "shrinking";
      case OPTIMIZING:
        return "optimization";
      case OBFUSCATING:
        return "obfuscation";
      case ACCESS_MODIFICATION:
        return "accessmodification";
      case ANNOTATION_REMOVAL:
        return "annotationremoval";
      default:
        throw new Unimplemented();
    }
  }

  private static String getTypePatternString(KeepTypePattern typePattern) {
    if (typePattern.isAny()) {
      return "***";
    }
    return descriptorToJavaType(typePattern.getDescriptor());
  }

  private static String descriptorToJavaType(String descriptor) {
    if (descriptor.isEmpty()) {
      throw new KeepEdgeException("Invalid empty type descriptor");
    }
    if (descriptor.length() == 1) {
      return primitiveDescriptorToJavaType(descriptor.charAt(0));
    }
    if (descriptor.charAt(0) == '[') {
      return arrayDescriptorToJavaType(descriptor);
    }
    return classDescriptorToJavaType(descriptor);
  }

  private static String primitiveDescriptorToJavaType(char descriptor) {
    switch (descriptor) {
      case 'Z':
        return "boolean";
      case 'B':
        return "byte";
      case 'S':
        return "short";
      case 'I':
        return "int";
      case 'J':
        return "long";
      case 'F':
        return "float";
      case 'D':
        return "double";
      default:
        throw new KeepEdgeException("Invalid primitive descriptor: " + descriptor);
    }
  }

  private static String classDescriptorToJavaType(String descriptor) {
    int last = descriptor.length() - 1;
    if (descriptor.charAt(0) != 'L' || descriptor.charAt(last) != ';') {
      throw new KeepEdgeException("Invalid class descriptor: " + descriptor);
    }
    return descriptor.substring(1, last).replace('/', '.');
  }

  private static String arrayDescriptorToJavaType(String descriptor) {
    for (int i = 0; i < descriptor.length(); i++) {
      char c = descriptor.charAt(i);
      if (c != '[') {
        StringBuilder builder = new StringBuilder();
        builder.append(descriptorToJavaType(descriptor.substring(i)));
        for (int j = 0; j < i; j++) {
          builder.append("[]");
        }
        return builder.toString();
      }
    }
    throw new KeepEdgeException("Invalid array descriptor: " + descriptor);
  }
}
