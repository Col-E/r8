// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class JavaSourceCodePrinter {

  private final Set<String> imports = new HashSet<>();
  private final String className;
  private final String packageName;
  private final String classModifiers;
  private final String header;
  private final StringBuilder bodyPrinter = new StringBuilder();

  public enum KnownType {
    Consumer("java.util.function", "Consumer", false),
    DexItemFactory("com.android.tools.r8.graph", "DexItemFactory", false),
    DexMethod("com.android.tools.r8.graph", "DexMethod", false);

    private String packageName;
    private String simpleName;
    private boolean isPrimitive;

    KnownType(String packageName, String simpleName, boolean isPrimitive) {
      this.packageName = packageName;
      this.simpleName = simpleName;
      this.isPrimitive = isPrimitive;
    }

    boolean isPrimitive() {
      return isPrimitive;
    }

    String getCanonicalName() {
      return packageName + "." + simpleName;
    }

    String getSimpleName() {
      return simpleName;
    }
  }

  public JavaSourceCodePrinter(
      String className, String packageName, String classModifiers, String header) {
    this.className = className;
    this.packageName = packageName;
    this.classModifiers = classModifiers;
    this.header = header;
  }

  public void addClassImport(KnownType type) {
    if (!type.isPrimitive) {
      imports.add(type.getCanonicalName());
    }
  }

  public JavaSourceCodePrinter addMethod(
      String modifiers,
      Type returnType,
      String name,
      List<MethodParameter> parameters,
      Consumer<JavaSourceCodeMethodPrinter> content) {
    bodyPrinter.append(modifiers).append(" ");
    if (returnType != null) {
      bodyPrinter.append(returnType.toString(this::addClassImport));
    } else {
      bodyPrinter.append("void");
    }
    JavaSourceCodeMethodPrinter javaSourceCodeMethodPrinter = new JavaSourceCodeMethodPrinter();
    content.accept(javaSourceCodeMethodPrinter);
    bodyPrinter
        .append(" ")
        .append(name)
        .append(
            StringUtils.join(
                ", ",
                parameters,
                parameter -> parameter.toString(this::addClassImport),
                BraceType.PARENS))
        .append(" {")
        .append(StringUtils.LINE_SEPARATOR)
        .append(javaSourceCodeMethodPrinter)
        .append("}")
        .append(StringUtils.LINE_SEPARATOR);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(header);
    if (packageName != null) {
      sb.append("package ").append(packageName).append(";").append(StringUtils.LINE_SEPARATOR);
    }
    sb.append(
            StringUtils.joinLines(
                imports.stream()
                    .sorted()
                    .map(imp -> "import " + imp + ";")
                    .collect(Collectors.toList())))
        .append(StringUtils.LINE_SEPARATOR);
    return sb.append(StringUtils.LINE_SEPARATOR)
        .append(classModifiers)
        .append(" ")
        .append("class ")
        .append(className)
        .append(" {")
        .append(StringUtils.LINE_SEPARATOR)
        .append(bodyPrinter)
        .append("}")
        .toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String className;
    private String packageName = null;
    private final String classModifiers = "public";
    private String header = "";

    public Builder setClassName(String className) {
      this.className = className;
      return this;
    }

    public Builder setPackageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    public Builder setHeader(String header) {
      this.header = header;
      return this;
    }

    public JavaSourceCodePrinter build() {
      assert className != null;
      return new JavaSourceCodePrinter(className, packageName, classModifiers, header);
    }
  }

  public static class JavaSourceCodeMethodPrinter {

    private final StringBuilder sb = new StringBuilder();

    public JavaSourceCodeMethodPrinter addInstanceMethodCall(
        String variable, String method, Action... content) {
      return addInstanceMethodCall(variable, method, Arrays.asList(content));
    }

    public JavaSourceCodeMethodPrinter addInstanceMethodCall(
        String variable, String method, Collection<Action> content) {
      sb.append(variable).append(".").append(method).append("(");
      boolean insertSeparator = false;
      for (Action action : content) {
        if (insertSeparator) {
          sb.append(", ");
        }
        action.execute();
        insertSeparator = true;
      }
      sb.append(")");
      return this;
    }

    public Action literal(String constant) {
      return () -> sb.append(StringUtils.quote(constant));
    }

    public JavaSourceCodeMethodPrinter addSemicolon() {
      sb.append(";");
      return this;
    }

    public JavaSourceCodeMethodPrinter newLine() {
      sb.append(StringUtils.LINE_SEPARATOR);
      return this;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }

  public static class Type {

    private final KnownType type;

    private Type(KnownType type) {
      this.type = type;
    }

    public static Type fromType(KnownType type) {
      return new Type(type);
    }

    public String toString(Consumer<KnownType> classConsumer) {
      classConsumer.accept(type);
      return type.getSimpleName();
    }
  }

  public static class ParameterizedType extends Type {

    private final Type[] arguments;

    private ParameterizedType(KnownType clazz, Type[] arguments) {
      super(clazz);
      this.arguments = arguments;
    }

    public static ParameterizedType fromType(KnownType type, Type... arguments) {
      return new ParameterizedType(type, arguments);
    }

    @Override
    public String toString(Consumer<KnownType> classConsumer) {
      StringBuilder sb = new StringBuilder();
      sb.append(super.toString(classConsumer));
      if (arguments.length == 0) {
        return sb.toString();
      }
      sb.append("<");
      sb.append(
          StringUtils.join(", ", Arrays.asList(arguments), type -> type.toString(classConsumer)));
      sb.append(">");
      return sb.toString();
    }
  }

  public static class MethodParameter {

    private final Type type;
    private final String name;

    private MethodParameter(Type type, String name) {
      this.type = type;
      this.name = name;
    }

    public static MethodParameter build(Type type, String name) {
      return new MethodParameter(type, name);
    }

    private String toString(Consumer<KnownType> classConsumer) {
      return type.toString(classConsumer) + " " + name;
    }
  }
}
