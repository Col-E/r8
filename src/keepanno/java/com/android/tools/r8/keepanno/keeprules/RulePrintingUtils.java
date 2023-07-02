// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepExtendsPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
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
import com.android.tools.r8.keepanno.ast.ModifierPattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class RulePrintingUtils {

  public static final String IF = "-if";
  public static final String KEEP = "-keep";
  public static final String KEEP_CLASS_MEMBERS = "-keepclassmembers";
  public static final String KEEP_CLASSES_WITH_MEMBERS = "-keepclasseswithmembers";
  public static final String CHECK_DISCARD = "-checkdiscard";

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
      printClassName(
          extendsPattern.asClassNamePattern(), RulePrinter.withoutBackReferences(builder));
    }
    return builder;
  }

  public static RulePrinter printMemberClause(KeepMemberPattern member, RulePrinter printer) {
    if (member.isAllMembers()) {
      // Note: the rule language does not allow backref to a full member. A rule matching all
      // members via a binding must be split in two up front: one for methods and one for fields.
      return printer.appendWithoutBackReferenceAssert("*").append(";");
    }
    if (member.isMethod()) {
      return printMethod(member.asMethod(), printer);
    }
    if (member.isField()) {
      return printField(member.asField(), printer);
    }
    // The pattern is a restricted member pattern, e.g., it must apply to fields and methods
    // without any specifics not common to both. For now that is just the access pattern.
    assert !member.getAccessPattern().isAny();
    printMemberAccess(printer, member.getAccessPattern());
    return printer.appendWithoutBackReferenceAssert("*").append(";");
  }

  private static RulePrinter printField(KeepFieldPattern fieldPattern, RulePrinter builder) {
    printFieldAccess(builder, fieldPattern.getAccessPattern());
    printType(builder, fieldPattern.getTypePattern().asType());
    builder.append(" ");
    printFieldName(builder, fieldPattern.getNamePattern());
    return builder.append(";");
  }

  private static RulePrinter printMethod(KeepMethodPattern methodPattern, RulePrinter builder) {
    printMethodAccess(builder, methodPattern.getAccessPattern());
    printReturnType(builder, methodPattern.getReturnTypePattern());
    builder.append(" ");
    printMethodName(builder, methodPattern.getNamePattern());
    printParameters(builder, methodPattern.getParametersPattern());
    return builder.append(";");
  }

  private static RulePrinter printParameters(
      RulePrinter builder, KeepMethodParametersPattern parametersPattern) {
    if (parametersPattern.isAny()) {
      return builder.appendAnyParameters();
    }
    builder.append("(");
    List<KeepTypePattern> patterns = parametersPattern.asList();
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      printType(builder, patterns.get(i));
    }
    return builder.append(")");
  }

  private static RulePrinter printFieldName(RulePrinter builder, KeepFieldNamePattern namePattern) {
    return namePattern.isAny()
        ? builder.appendStar()
        : builder.append(namePattern.asExact().getName());
  }

  private static RulePrinter printMethodName(
      RulePrinter builder, KeepMethodNamePattern namePattern) {
    return namePattern.isAny()
        ? builder.appendStar()
        : builder.append(namePattern.asExact().getName());
  }

  private static RulePrinter printReturnType(
      RulePrinter builder, KeepMethodReturnTypePattern returnTypePattern) {
    if (returnTypePattern.isVoid()) {
      return builder.append("void");
    }
    return printType(builder, returnTypePattern.asType());
  }

  private static RulePrinter printType(RulePrinter builder, KeepTypePattern typePattern) {
    if (typePattern.isAny()) {
      return builder.appendTripleStar();
    }
    return builder.append(descriptorToJavaType(typePattern.getDescriptor()));
  }

  public static RulePrinter printMemberAccess(
      RulePrinter printer, KeepMemberAccessPattern accessPattern) {
    if (accessPattern.isAny()) {
      // No text will match any access pattern.
      // Don't print the indent in this case.
      return printer;
    }
    printVisibilityModifiers(printer, accessPattern);
    printModifier(printer, accessPattern.getStaticPattern(), "static");
    printModifier(printer, accessPattern.getFinalPattern(), "final");
    printModifier(printer, accessPattern.getSyntheticPattern(), "synthetic");
    return printer;
  }

  public static void printVisibilityModifiers(
      RulePrinter printer, KeepMemberAccessPattern accessPattern) {
    if (accessPattern.isAnyVisibility()) {
      return;
    }
    Set<AccessVisibility> allowed = accessPattern.getAllowedAccessVisibilities();
    // Package private does not have an actual representation it must be matched by its absence.
    // Thus, in the case of package-private the match is the negation of those not-present.
    boolean negated = allowed.contains(AccessVisibility.PACKAGE_PRIVATE);
    for (AccessVisibility visibility : AccessVisibility.values()) {
      if (!visibility.equals(AccessVisibility.PACKAGE_PRIVATE)) {
        if (!negated == allowed.contains(visibility)) {
          if (negated) {
            printer.append("!");
          }
          printer.append(visibility.toSourceSyntax()).append(" ");
        }
      }
    }
  }

  public static void printModifier(
      RulePrinter printer, ModifierPattern modifierPattern, String syntax) {
    if (modifierPattern.isAny()) {
      return;
    }
    if (modifierPattern.isOnlyNegative()) {
      printer.append("!");
    }
    printer.append(syntax).append(" ");
  }

  public static RulePrinter printMethodAccess(
      RulePrinter printer, KeepMethodAccessPattern accessPattern) {
    printMemberAccess(printer, accessPattern);
    printModifier(printer, accessPattern.getSynchronizedPattern(), "synchronized");
    printModifier(printer, accessPattern.getBridgePattern(), "bridge");
    printModifier(printer, accessPattern.getNativePattern(), "native");
    printModifier(printer, accessPattern.getAbstractPattern(), "abstract");
    printModifier(printer, accessPattern.getStrictFpPattern(), "strictfp");
    return printer;
  }

  public static RulePrinter printFieldAccess(
      RulePrinter printer, KeepFieldAccessPattern accessPattern) {
    printMemberAccess(printer, accessPattern);
    RulePrintingUtils.printModifier(printer, accessPattern.getVolatilePattern(), "volatile");
    RulePrintingUtils.printModifier(printer, accessPattern.getTransientPattern(), "transient");
    return printer;
  }

  public static RulePrinter printClassName(
      KeepQualifiedClassNamePattern classNamePattern, RulePrinter printer) {
    if (classNamePattern.isAny()) {
      return printer.appendStar();
    }
    printPackagePrefix(classNamePattern.getPackagePattern(), printer);
    return printSimpleClassName(classNamePattern.getNamePattern(), printer);
  }

  private static RulePrinter printPackagePrefix(
      KeepPackagePattern packagePattern, RulePrinter builder) {
    if (packagePattern.isAny()) {
      return builder.appendDoubleStar().append(".");
    }
    if (packagePattern.isTop()) {
      return builder;
    }
    assert packagePattern.isExact();
    return builder.append(packagePattern.getExactPackageAsString()).append(".");
  }

  private static RulePrinter printSimpleClassName(
      KeepUnqualfiedClassNamePattern namePattern, RulePrinter builder) {
    if (namePattern.isAny()) {
      return builder.appendStar();
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
