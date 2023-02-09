// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class GenerateHtmlDoc extends AbstractGenerateFiles {

  public GenerateHtmlDoc(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory);
  }

  private static class StringBuilderWithIndent {

    String NL = System.lineSeparator();
    StringBuilder builder = new StringBuilder();
    String indent = "";

    StringBuilderWithIndent() {}

    StringBuilderWithIndent indent(String indent) {
      this.indent = indent;
      return this;
    }

    StringBuilderWithIndent appendLineStart(String lineStart) {
      builder.append(indent);
      builder.append(lineStart);
      return this;
    }

    StringBuilderWithIndent append(String string) {
      builder.append(string);
      return this;
    }

    StringBuilderWithIndent appendLineEnd(String lineEnd) {
      builder.append(lineEnd);
      builder.append(NL);
      return this;
    }

    StringBuilderWithIndent appendLine(String line) {
      builder.append(indent);
      builder.append(line);
      builder.append(NL);
      return this;
    }

    StringBuilderWithIndent emptyLine() {
      builder.append(NL);
      return this;
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }

  private abstract static class SourceBuilder<B extends GenerateHtmlDoc.SourceBuilder> {

    protected final DexClass clazz;
    protected final boolean newClass;
    protected List<DexEncodedField> fields = new ArrayList<>();
    protected List<DexEncodedMethod> constructors = new ArrayList<>();
    protected List<DexEncodedMethod> methods = new ArrayList<>();

    String className;
    String packageName;

    private SourceBuilder(DexClass clazz, boolean newClass) {
      this.clazz = clazz;
      this.newClass = newClass;
      this.className = clazz.type.toSourceString();
      int index = this.className.lastIndexOf('.');
      this.packageName = index > 0 ? this.className.substring(0, index) : "";
    }

    public abstract B self();

    private B addField(DexEncodedField field) {
      fields.add(field);
      return self();
    }

    private B addMethod(DexEncodedMethod method) {
      assert !method.isClassInitializer();
      if (method.isInitializer()) {
        constructors.add(method);
      } else {
        methods.add(method);
      }
      return self();
    }

    protected String typeInPackage(String typeName, String packageName) {
      if (typeName.startsWith(packageName)
          && typeName.length() > packageName.length()
          && typeName.charAt(packageName.length()) == '.'
          && typeName.indexOf('.', packageName.length() + 1) == -1) {
        return typeName.substring(packageName.length() + 1);
      }
      return null;
    }

    protected String typeInPackage(String typeName) {
      String result = typeInPackage(typeName, packageName);
      if (result == null) {
        result = typeInPackage(typeName, "java.lang");
      }
      if (result == null) {
        result = typeName;
      }
      return result.replace('$', '.');
    }

    protected String typeInPackage(DexType type) {
      if (type.isPrimitiveType()) {
        return type.toSourceString();
      }
      return typeInPackage(type.toSourceString());
    }

    protected String accessFlags(ClassAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isAbstract() && !accessFlags.isInterface()) {
        flags.add("abstract");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    protected String accessFlags(FieldAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    protected String accessFlags(MethodAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isAbstract()) {
        flags.add("abstract");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    public String arguments(DexEncodedMethod method) {
      DexProto proto = method.getReference().proto;
      StringBuilder argsBuilder = new StringBuilder();
      boolean firstArg = true;
      int argIndex = method.isVirtualMethod() || method.accessFlags.isConstructor() ? 1 : 0;
      int argNumber = 0;
      argsBuilder.append("(");
      for (DexType type : proto.parameters.values) {
        if (!firstArg) {
          argsBuilder.append(", ");
        }
        if (method.hasCode()) {
          String name = "p" + argNumber;
          for (LocalVariableInfo localVariable : method.getCode().asCfCode().getLocalVariables()) {
            if (localVariable.getIndex() == argIndex) {
              assert !localVariable.getLocal().name.toString().equals("this");
              name = localVariable.getLocal().name.toString();
            }
          }
          argsBuilder.append(typeInPackage(type)).append(" ").append(name);
        } else {
          argsBuilder.append(typeInPackage(type)).append(" p").append(argNumber);
        }
        firstArg = false;
        argIndex += type.isWideType() ? 2 : 1;
        argNumber++;
      }
      argsBuilder.append(")");
      return argsBuilder.toString();
    }
  }

  private static class HTMLBuilder extends StringBuilderWithIndent {

    private String indent = "";

    private void increaseIndent() {
      indent += "  ";
      indent(indent);
    }

    private void decreaseIndent() {
      indent = indent.substring(0, indent.length() - 2);
      indent(indent);
    }

    HTMLBuilder appendTdPackage(String s) {
      appendLineStart("<td><code><em>" + s + "</em></code><br>");
      if (s.startsWith("java.time")) {
        append("<a href=\"#java-time-customizations\">See customizations</a><br");
      } else if (s.startsWith("java.nio")) {
        append("<a href=\"#java-nio-customizations\">See customizations</a><br");
      }
      return this;
    }

    HTMLBuilder appendTdClassName(String s) {
      appendLineEnd(
          "<code><br><br><div style=\"font-size:small;font-weight:bold;\">&nbsp;"
              + s
              + "</div></code><br><br></td>");
      return this;
    }

    HTMLBuilder appendTdP(String s) {
      appendLine("<td><p>" + s + "</p></td>");
      return this;
    }

    HTMLBuilder appendLiCode(String s) {
      appendLine("<li class=\"java8_table\"><code>" + s + "</code></li>");
      return this;
    }

    HTMLBuilder start(String tag) {
      appendLine("<" + tag + ">");
      increaseIndent();
      return this;
    }

    HTMLBuilder end(String tag) {
      decreaseIndent();
      appendLine("</" + tag + ">");
      return this;
    }
  }

  public static class HTMLSourceBuilder extends SourceBuilder<HTMLSourceBuilder> {

    private final Set<DexMethod> parallelMethods;

    public HTMLSourceBuilder(DexClass clazz, boolean newClass, Set<DexMethod> parallelMethods) {
      super(clazz, newClass);
      this.parallelMethods = parallelMethods;
    }

    @Override
    public HTMLSourceBuilder self() {
      return this;
    }

    @Override
    public String toString() {
      HTMLBuilder builder = new HTMLBuilder();
      builder.start("tr");
      if (packageName.length() > 0) {
        builder.appendTdPackage(packageName);
      }
      builder.appendTdClassName(typeInPackage(className));
      builder
          .start("td")
          .start(
              "ul style=\"list-style-position:inside; list-style-type: none !important;"
                  + " margin-left:0px;padding-left:0px !important;\"");
      if (!fields.isEmpty()) {
        assert newClass; // Currently no fields are added to existing classes.
        for (DexEncodedField field : fields) {
          builder.appendLiCode(
              accessFlags(field.accessFlags)
                  + " "
                  + typeInPackage(field.getReference().type)
                  + " "
                  + field.getReference().name);
        }
      }
      if (!constructors.isEmpty()) {
        for (DexEncodedMethod constructor : constructors) {
          builder.appendLiCode(
              accessFlags(constructor.accessFlags)
                  + " "
                  + typeInPackage(className)
                  + arguments(constructor));
        }
      }
      List<String> parallelM = new ArrayList<>();
      if (!methods.isEmpty()) {
        for (DexEncodedMethod method : methods) {
          builder.appendLiCode(
              accessFlags(method.accessFlags)
                  + " "
                  + typeInPackage(method.getReference().proto.returnType)
                  + " "
                  + method.getReference().name
                  + arguments(method));
          if (parallelMethods.contains(method.getReference())) {
            parallelM.add(method.getReference().name.toString());
          }
        }
      }
      builder.end("ul").end("td");
      StringBuilder commentBuilder = new StringBuilder();
      if (newClass) {
        commentBuilder.append("Fully implemented class.");
      } else {
        commentBuilder.append("Additional methods on existing class.");
      }
      if (!parallelM.isEmpty()) {
        commentBuilder.append(newClass ? "" : "<br>");
        if (parallelM.size() == 1) {
          commentBuilder
              .append("The method <code>")
              .append(parallelM.get(0))
              .append("</code> is only supported from API level 21.");
        } else {
          commentBuilder.append("The following methods are only supported from API level 21:<br>");
          for (int i = 0; i < parallelM.size(); i++) {
            commentBuilder.append("<code>").append(parallelM.get(i)).append("</code><br>");
          }
        }
      }
      builder.appendTdP(commentBuilder.toString());
      builder.end("tr");
      return builder.toString();
    }
  }

  private void generateClassHTML(
      PrintStream ps,
      DexClass clazz,
      boolean newClass,
      Predicate<DexEncodedField> fieldsFilter,
      Predicate<DexEncodedMethod> methodsFilter) {
    SourceBuilder builder = new HTMLSourceBuilder(clazz, newClass, parallelMethods);
    StreamSupport.stream(clazz.fields().spliterator(), false)
        .filter(fieldsFilter)
        .filter(field -> field.accessFlags.isPublic() || field.accessFlags.isProtected())
        .sorted(Comparator.comparing(DexEncodedField::toSourceString))
        .forEach(builder::addField);
    StreamSupport.stream(clazz.methods().spliterator(), false)
        .filter(methodsFilter)
        .filter(
            method ->
                (method.accessFlags.isPublic() || method.accessFlags.isProtected())
                    && !method.accessFlags.isBridge())
        .sorted(Comparator.comparing(DexEncodedMethod::toSourceString))
        .forEach(builder::addMethod);
    ps.println(builder);
  }

  void run() throws Exception {
    PrintStream ps = new PrintStream(Files.newOutputStream(outputDirectory.resolve("apis.html")));
    // Full classes added.
    SupportedMethods supportedMethods =
        collectSupportedMethods(MAX_TESTED_ANDROID_API_LEVEL, x -> true);
    supportedMethods.classesWithAllMethodsSupported.stream()
        .sorted(Comparator.comparing(clazz -> clazz.type.toSourceString()))
        .forEach(clazz -> generateClassHTML(ps, clazz, true, field -> true, method -> true));

    // Methods added to existing classes.
    supportedMethods.supportedMethods.keySet().stream()
        .filter(clazz -> !supportedMethods.classesWithAllMethodsSupported.contains(clazz))
        .sorted(Comparator.comparing(clazz -> clazz.type.toSourceString()))
        .forEach(
            clazz ->
                generateClassHTML(
                    ps,
                    clazz,
                    false,
                    field -> false,
                    method -> supportedMethods.supportedMethods.get(clazz).contains(method)));
  }

  public static void main(String[] args) throws Exception {
    AbstractGenerateFiles.main(args);
  }
}
