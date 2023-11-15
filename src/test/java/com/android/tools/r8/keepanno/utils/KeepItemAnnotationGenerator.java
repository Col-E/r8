// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cfmethodgeneration.CodeGenerationBase;
import com.android.tools.r8.keepanno.annotations.CheckOptimizedOut;
import com.android.tools.r8.keepanno.annotations.CheckRemoved;
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
import com.android.tools.r8.keepanno.annotations.UsedByNative;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public class KeepItemAnnotationGenerator {

  public static void main(String[] args) throws IOException {
    Generator.class.getClassLoader().setDefaultAssertionStatus(true);
    Generator.run();
  }

  private static String quote(String str) {
    return "\"" + str + "\"";
  }

  private static String simpleName(Class<?> clazz) {
    return clazz.getSimpleName();
  }

  private static class GroupMember {

    final String name;
    String docTitle = null;
    final List<String> docLines = new ArrayList<>();
    String valueType = null;
    String valueDefault = null;

    GroupMember(String name) {
      this.name = name;
    }

    void generate(Generator generator) {
      assert docLines.isEmpty() || docTitle != null;
      if (docTitle != null) {
        List<String> doc = new ArrayList<>();
        doc.add(docTitle);
        doc.addAll(docLines);
        generator.printDocString(doc.toArray(new String[0]));
      }
      if (valueDefault == null) {
        generator.println(valueType + " " + name + "();");
      } else {
        generator.println(valueType + " " + name + "() default " + valueDefault + ";");
      }
    }

    public void generateConstants(Generator generator) {
      generator.println("public static final String " + name + " = " + quote(name) + ";");
    }

    public GroupMember clearDocLines() {
      docLines.clear();
      return this;
    }

    public GroupMember setDocTitle(String title) {
      assert docTitle == null;
      assert title.endsWith(".");
      docTitle = title;
      return this;
    }

    public GroupMember addDocList(List<String> items) {
      docLines.add("<ul>");
      for (String item : items) {
        docLines.add("<li>" + item);
      }
      docLines.add("</ul>");
      return this;
    }

    public GroupMember addDocList(String... items) {
      return addDocList(Arrays.asList(items));
    }

    public GroupMember addDoc(String... strings) {
      for (String string : strings) {
        docLines.add(string);
      }
      return this;
    }

    public GroupMember requiredStringValue() {
      assert valueDefault == null;
      return defaultType("String");
    }

    public GroupMember requiredValueOfType(String type) {
      assert valueDefault == null;
      return defaultType(type);
    }

    public GroupMember requiredValueOfType(Class<?> type) {
      assert valueDefault == null;
      return defaultType(simpleName(type));
    }

    public GroupMember requiredValueOfArrayType(Class<?> type) {
      assert valueDefault == null;
      return defaultType(simpleName(type) + "[]");
    }

    public GroupMember defaultType(String type) {
      valueType = type;
      return this;
    }

    public GroupMember defaultValue(String value) {
      valueDefault = value;
      return this;
    }

    public GroupMember defaultEmptyString() {
      return defaultType("String").defaultValue(quote(""));
    }

    public GroupMember defaultObjectClass() {
      return defaultType("Class<?>").defaultValue("Object.class");
    }

    public GroupMember defaultEmptyArray(String valueType) {
      return defaultType(valueType + "[]").defaultValue("{}");
    }

    public GroupMember defaultEmptyArray(Class<?> type) {
      return defaultEmptyArray(simpleName(type));
    }
  }

  private static class Group {

    final String name;
    final List<GroupMember> members = new ArrayList<>();
    final List<String> footers = new ArrayList<>();
    final LinkedHashMap<String, Group> mutuallyExclusiveGroups = new LinkedHashMap<>();

    private Group(String name) {
      this.name = name;
    }

    Group addMember(GroupMember member) {
      members.add(member);
      return this;
    }

    Group addDocFooter(String footer) {
      footers.add(footer);
      return this;
    }

    void generate(Generator generator) {
      assert !members.isEmpty();
      for (GroupMember member : members) {
        if (member != members.get(0)) {
          generator.println();
        }
        List<String> mutuallyExclusiveProperties = new ArrayList<>();
        for (GroupMember other : members) {
          if (!member.name.equals(other.name)) {
            mutuallyExclusiveProperties.add(other.name);
          }
        }
        mutuallyExclusiveGroups.forEach(
            (unused, group) -> {
              group.members.forEach(m -> mutuallyExclusiveProperties.add(m.name));
            });
        if (mutuallyExclusiveProperties.size() == 1) {
          member.addDoc(
              "<p>Mutually exclusive with the property `"
                  + mutuallyExclusiveProperties.get(0)
                  + "` also defining "
                  + name
                  + ".");
        } else if (mutuallyExclusiveProperties.size() > 1) {
          StringBuilder mutualExclusiveDoc =
              new StringBuilder()
                  .append("<p>Mutually exclusive with the following other properties defining ")
                  .append(name)
                  .append(":");
          member.addDoc(mutualExclusiveDoc.toString());
          member.addDocList(mutuallyExclusiveProperties);
        }
        footers.forEach(member::addDoc);
        member.generate(generator);
      }
    }

    void generateConstants(Generator generator) {
      for (GroupMember member : members) {
        member.generateConstants(generator);
      }
    }

    public void addMutuallyExclusiveGroups(Group... groups) {
      for (Group group : groups) {
        mutuallyExclusiveGroups.computeIfAbsent(
            group.name,
            k -> {
              // Mutually exclusive is bidirectional so link in with other group.
              group.mutuallyExclusiveGroups.put(name, this);
              return group;
            });
      }
    }
  }

  private static class Generator {

    private static final List<Class<?>> ANNOTATION_IMPORTS =
        ImmutableList.of(ElementType.class, Retention.class, RetentionPolicy.class, Target.class);

    private final PrintStream writer;
    private int indent = 0;

    public Generator(PrintStream writer) {
      this.writer = writer;
    }

    private void withIndent(Runnable fn) {
      indent += 2;
      fn.run();
      indent -= 2;
    }

    private void println() {
      // Don't indent empty lines.
      writer.println();
    }

    private void println(String line) {
      assert line.length() > 0;
      writer.print(Strings.repeat(" ", indent));
      writer.println(line);
    }

    public void printDocString(String... lines) {
      assert lines.length > 0;
      if (lines.length == 1) {
        println("/** " + lines[0] + " */");
        return;
      }
      println("/**");
      println(" * " + lines[0]);
      for (int i = 1; i < lines.length; i++) {
        String line = lines[i];
        if (line.startsWith("<p>")) {
          println(" *");
        }
        println(" * " + line);
      }
      println(" */");
    }

    private void printCopyRight(int year) {
      println(
          CodeGenerationBase.getHeaderString(
              year, KeepItemAnnotationGenerator.class.getSimpleName()));
    }

    private void printPackage(String pkg) {
      println("package com.android.tools.r8.keepanno." + pkg + ";");
      println();
    }

    private void printImports(Class<?>... imports) {
      printImports(Arrays.asList(imports));
    }

    private void printImports(List<Class<?>> imports) {
      for (Class<?> clazz : imports) {
        println("import " + clazz.getCanonicalName() + ";");
      }
      println();
    }

    private static String KIND_GROUP = "kind";
    private static String OPTIONS_GROUP = "options";
    private static String CLASS_GROUP = "class";
    private static String CLASS_NAME_GROUP = "class-name";
    private static String INSTANCE_OF_GROUP = "instance-of";

    private Group createDescriptionGroup() {
      return new Group("description")
          .addMember(
              new GroupMember("description")
                  .setDocTitle("Optional description to document the reason for this annotation.")
                  .defaultEmptyString());
    }

    private Group createBindingsGroup() {
      return new Group("bindings")
          .addMember(new GroupMember("bindings").defaultEmptyArray(KeepBinding.class));
    }

    private Group createPreconditionsGroup() {
      return new Group("preconditions")
          .addMember(
              new GroupMember("preconditions")
                  .setDocTitle(
                      "Conditions that should be satisfied for the annotation to be in effect.")
                  .addDoc("<p>Defaults to no conditions, thus trivially/unconditionally satisfied.")
                  .defaultEmptyArray(KeepCondition.class));
    }

    private Group createConsequencesGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("consequences")
                  .setDocTitle("Consequences that must be kept if the annotation is in effect.")
                  .requiredValueOfArrayType(KeepTarget.class));
    }

    private Group createConsequencesAsValueGroup() {
      return new Group("consequences")
          .addMember(
              new GroupMember("value")
                  .setDocTitle("Targets that must be kept if the annotation is in effect.")
                  .requiredValueOfArrayType(KeepTarget.class));
    }

    private Group createAdditionalPreconditionsGroup() {
      return new Group("additional-preconditions")
          .addMember(
              new GroupMember("additionalPreconditions")
                  .setDocTitle("Additional preconditions for the annotation to be in effect.")
                  .addDoc("<p>Defaults to no additional preconditions.")
                  .defaultEmptyArray("KeepTarget"));
    }

    private Group createAdditionalTargetsGroup(String docTitle) {
      return new Group("additional-targets")
          .addMember(
              new GroupMember("additionalTargets")
                  .setDocTitle(docTitle)
                  .addDoc("<p>Defaults to no additional targets.")
                  .defaultEmptyArray("KeepTarget"));
    }

    private Group getKindGroup() {
      return new Group(KIND_GROUP).addMember(getKindMember());
    }

    private static GroupMember getKindMember() {
      return new GroupMember("kind")
          .defaultType("KeepItemKind")
          .defaultValue("KeepItemKind.DEFAULT")
          .setDocTitle("Specify the kind of this item pattern.")
          .addDoc("<p>Possible values are:")
          .addDocList(
              KeepItemKind.ONLY_CLASS.name(),
              KeepItemKind.ONLY_MEMBERS.name(),
              KeepItemKind.CLASS_AND_MEMBERS.name())
          .addDoc(
              "<p>If unspecified the default for an item with no member patterns is",
              KeepItemKind.ONLY_CLASS.name(),
              "and if it does have member patterns the default is",
              KeepItemKind.ONLY_MEMBERS.name());
    }

    private Group getKeepOptionsGroup() {
      return new Group(OPTIONS_GROUP)
          .addMember(
              new GroupMember("allow")
                  .setDocTitle(
                      "Define the "
                          + OPTIONS_GROUP
                          + " that do not need to be preserved for the target.")
                  .defaultEmptyArray("KeepOption"))
          .addMember(
              new GroupMember("disallow")
                  .setDocTitle(
                      "Define the " + OPTIONS_GROUP + " that *must* be preserved for the target.")
                  .defaultEmptyArray("KeepOption"))
          .addDocFooter(
              "<p>If nothing is specified for "
                  + OPTIONS_GROUP
                  + " the default is "
                  + quote("allow none")
                  + " / "
                  + quote("disallow all")
                  + ".");
    }

    private GroupMember bindingName() {
      return new GroupMember("bindingName")
          .setDocTitle(
              "Name with which other bindings, conditions or targets "
                  + "can reference the bound item pattern.")
          .requiredStringValue();
    }

    private GroupMember classFromBinding() {
      return new GroupMember("classFromBinding")
          .setDocTitle("Define the " + CLASS_GROUP + " pattern by reference to a binding.")
          .defaultEmptyString();
    }

    private Group createClassBindingGroup() {
      return new Group(CLASS_GROUP)
          .addMember(classFromBinding())
          .addDocFooter("<p>If none are specified the default is to match any class.");
    }

    private GroupMember className() {
      return new GroupMember("className")
          .setDocTitle("Define the " + CLASS_NAME_GROUP + " pattern by fully qualified class name.")
          .defaultEmptyString();
    }

    private GroupMember classConstant() {
      return new GroupMember("classConstant")
          .setDocTitle(
              "Define the " + CLASS_NAME_GROUP + " pattern by reference to a Class constant.")
          .defaultObjectClass();
    }

    private Group createClassNamePatternGroup() {
      return new Group(CLASS_NAME_GROUP)
          .addMember(className())
          .addMember(classConstant())
          .addDocFooter("<p>If none are specified the default is to match any class name.");
    }

    private GroupMember instanceOfClassName() {
      return new GroupMember("instanceOfClassName")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstant() {
      return new GroupMember("instanceOfClassConstant")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .defaultObjectClass();
    }

    private String getInstanceOfExclusiveDoc() {
      return "<p>The pattern is exclusive in that it does not match classes that are"
          + " instances of the pattern, but only those that are instances of classes that"
          + " are subclasses of the pattern.";
    }

    private GroupMember instanceOfClassNameExclusive() {
      return new GroupMember("instanceOfClassNameExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances of the fully qualified class name.")
          .addDoc(getInstanceOfExclusiveDoc())
          .defaultEmptyString();
    }

    private GroupMember instanceOfClassConstantExclusive() {
      return new GroupMember("instanceOfClassConstantExclusive")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes that are instances the referenced Class constant.")
          .addDoc(getInstanceOfExclusiveDoc())
          .defaultObjectClass();
    }

    private GroupMember extendsClassName() {
      return new GroupMember("extendsClassName")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes extending the fully qualified class name.")
          .addDoc(getInstanceOfExclusiveDoc())
          .addDoc("<p>This property is deprecated, use instanceOfClassName instead.")
          .defaultEmptyString();
    }

    private GroupMember extendsClassConstant() {
      return new GroupMember("extendsClassConstant")
          .setDocTitle(
              "Define the "
                  + INSTANCE_OF_GROUP
                  + " pattern as classes extending the referenced Class constant.")
          .addDoc(getInstanceOfExclusiveDoc())
          .addDoc("<p>This property is deprecated, use instanceOfClassConstant instead.")
          .defaultObjectClass();
    }

    private Group createClassInstanceOfPatternGroup() {
      return new Group(INSTANCE_OF_GROUP)
          .addMember(instanceOfClassName())
          .addMember(instanceOfClassNameExclusive())
          .addMember(instanceOfClassConstant())
          .addMember(instanceOfClassConstantExclusive())
          .addMember(extendsClassName())
          .addMember(extendsClassConstant())
          .addDocFooter("<p>If none are specified the default is to match any class instance.");
    }

    private Group createMemberBindingGroup() {
      return new Group("member")
          .addMember(
              new GroupMember("memberFromBinding")
                  .setDocTitle("Define the member pattern in full by a reference to a binding.")
                  .addDoc(
                      "<p>Mutually exclusive with all other class and member pattern properties.",
                      "When a member binding is referenced this item is defined to be that item,",
                      "including its class and member patterns.")
                  .defaultEmptyString());
    }

    private Group createMemberAccessGroup() {
      return new Group("member-access")
          .addMember(
              new GroupMember("memberAccess")
                  .setDocTitle("Define the member-access pattern by matching on access flags.")
                  .addDoc(
                      "<p>Mutually exclusive with all field and method properties",
                      "as use restricts the match to both types of members.")
                  .defaultEmptyArray("MemberAccessFlags"));
    }

    private String getMutuallyExclusiveForMethodProperties() {
      return "<p>Mutually exclusive with all field properties.";
    }

    private String getMutuallyExclusiveForFieldProperties() {
      return "<p>Mutually exclusive with all method properties.";
    }

    private String getMethodDefaultDoc(String suffix) {
      return "<p>If none, and other properties define this item as a method, the default matches "
          + suffix
          + ".";
    }

    private String getFieldDefaultDoc(String suffix) {
      return "<p>If none, and other properties define this item as a field, the default matches "
          + suffix
          + ".";
    }

    private Group createMethodAccessGroup() {
      return new Group("method-access")
          .addMember(
              new GroupMember("methodAccess")
                  .setDocTitle("Define the method-access pattern by matching on access flags.")
                  .addDoc(getMutuallyExclusiveForMethodProperties())
                  .addDoc(getMethodDefaultDoc("any method-access flags"))
                  .defaultEmptyArray("MethodAccessFlags"));
    }

    private Group createMethodNameGroup() {
      return new Group("method-name")
          .addMember(
              new GroupMember("methodName")
                  .setDocTitle("Define the method-name pattern by an exact method name.")
                  .addDoc(getMutuallyExclusiveForMethodProperties())
                  .addDoc(getMethodDefaultDoc("any method name"))
                  .defaultEmptyString());
    }

    private Group createMethodReturnTypeGroup() {
      return new Group("return-type")
          .addMember(
              new GroupMember("methodReturnType")
                  .setDocTitle(
                      "Define the method return-type pattern by a fully qualified type or 'void'.")
                  .addDoc(getMutuallyExclusiveForMethodProperties())
                  .addDoc(getMethodDefaultDoc("any return type"))
                  .defaultEmptyString());
    }

    private Group createMethodParametersGroup() {
      return new Group("parameters")
          .addMember(
              new GroupMember("methodParameters")
                  .setDocTitle(
                      "Define the method parameters pattern by a list of fully qualified types.")
                  .addDoc(getMutuallyExclusiveForMethodProperties())
                  .addDoc(getMethodDefaultDoc("any parameters"))
                  .defaultType("String[]")
                  .defaultValue("{\"<default>\"}"));
    }

    private Group createFieldAccessGroup() {
      return new Group("field-access")
          .addMember(
              new GroupMember("fieldAccess")
                  .setDocTitle("Define the field-access pattern by matching on access flags.")
                  .addDoc(getMutuallyExclusiveForFieldProperties())
                  .addDoc(getFieldDefaultDoc("any field-access flags"))
                  .defaultEmptyArray("FieldAccessFlags"));
    }

    private Group createFieldNameGroup() {
      return new Group("field-name")
          .addMember(
              new GroupMember("fieldName")
                  .setDocTitle("Define the field-name pattern by an exact field name.")
                  .addDoc(getMutuallyExclusiveForFieldProperties())
                  .addDoc(getFieldDefaultDoc("any field name"))
                  .defaultEmptyString());
    }

    private Group createFieldTypeGroup() {
      return new Group("field-type")
          .addMember(
              new GroupMember("fieldType")
                  .setDocTitle("Define the field-type pattern by a fully qualified type.")
                  .addDoc(getMutuallyExclusiveForFieldProperties())
                  .addDoc(getFieldDefaultDoc("any type"))
                  .defaultEmptyString());
    }

    private void generateClassAndMemberPropertiesWithClassAndMemberBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(true);
    }

    private void generateClassAndMemberPropertiesWithClassBinding() {
      internalGenerateClassAndMemberPropertiesWithBinding(false);
    }

    private void internalGenerateClassAndMemberPropertiesWithBinding(boolean includeMemberBinding) {
      // Class properties.
      {
        Group bindingGroup = createClassBindingGroup();
        Group classNameGroup = createClassNamePatternGroup();
        Group classInstanceOfGroup = createClassInstanceOfPatternGroup();
        bindingGroup.addMutuallyExclusiveGroups(classNameGroup, classInstanceOfGroup);

        bindingGroup.generate(this);
        println();
        classNameGroup.generate(this);
        println();
        classInstanceOfGroup.generate(this);
        println();
      }

      // Member binding properties.
      if (includeMemberBinding) {
        createMemberBindingGroup().generate(this);
        println();
      }

      // The remaining member properties.
      generateMemberPropertiesNoBinding();
    }

    private void generateMemberPropertiesNoBinding() {
      // General member properties.
      createMemberAccessGroup().generate(this);
      println();

      // Method properties.
      createMethodAccessGroup().generate(this);
      println();
      createMethodNameGroup().generate(this);
      println();
      createMethodReturnTypeGroup().generate(this);
      println();
      createMethodParametersGroup().generate(this);
      println();

      // Field properties.
      createFieldAccessGroup().generate(this);
      println();
      createFieldNameGroup().generate(this);
      println();
      createFieldTypeGroup().generate(this);
    }

    private void generateKeepBinding() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      printDocString(
          "A binding of a keep item.",
          "<p>Bindings allow referencing the exact instance of a match from a condition in other "
              + " conditions and/or targets. It can also be used to reduce duplication of targets"
              + " by sharing patterns.",
          "<p>An item can be:",
          "<ul>",
          "  <li> a pattern on classes;",
          "  <li> a pattern on methods; or",
          "  <li> a pattern on fields.",
          "</ul>");
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepBinding {");
      println();
      withIndent(
          () -> {
            new GroupMember("bindingName")
                .setDocTitle(
                    "Name with which other bindings, conditions or targets can reference the bound"
                        + " item pattern.")
                .requiredValueOfType("String")
                .generate(this);
            println();
            getKindGroup().generate(this);
            println();
            generateClassAndMemberPropertiesWithClassBinding();
          });
      println();
      println("}");
    }

    private void generateKeepTarget() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      printDocString(
          "A target for a keep edge.",
          "<p>The target denotes an item along with options for what to keep. An item can be:",
          "<ul>",
          "  <li> a pattern on classes;",
          "  <li> a pattern on methods; or",
          "  <li> a pattern on fields.",
          "</ul>");
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepTarget {");
      println();
      withIndent(
          () -> {
            getKindGroup().generate(this);
            println();
            getKeepOptionsGroup().generate(this);
            println();
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      println();
      println("}");
    }

    private void generateKeepCondition() {
      printCopyRight(2022);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      printDocString(
          "A condition for a keep edge.",
          "<p>The condition denotes an item used as a precondition of a rule. An item can be:",
          "<ul>",
          "  <li> a pattern on classes;",
          "  <li> a pattern on methods; or",
          "  <li> a pattern on fields.",
          "</ul>");
      println("@Target(ElementType.ANNOTATION_TYPE)");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepCondition {");
      println();
      withIndent(
          () -> {
            generateClassAndMemberPropertiesWithClassAndMemberBinding();
          });
      println();
      println("}");
    }

    private void generateKeepForApi() {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      printDocString(
          "Annotation to mark a class, field or method as part of a library API surface.",
          "<p>When a class is annotated, member patterns can be used to define which members are to"
              + " be kept. When no member patterns are specified the default pattern matches all"
              + " public and protected members.",
          "<p>When a member is annotated, the member patterns cannot be used as the annotated"
              + " member itself fully defines the item to be kept (i.e., itself).");
      println(
          "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
              + " ElementType.CONSTRUCTOR})");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface KeepForApi {");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept as part of the API surface.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addDoc(
                    "<p>Default kind is",
                    KeepItemKind.CLASS_AND_MEMBERS.name(),
                    ", meaning the annotated class and/or member is to be kept.",
                    "When annotating a class this can be set to",
                    KeepItemKind.ONLY_CLASS.name(),
                    "to avoid patterns on any members.",
                    "That can be useful when the API members are themselves explicitly annotated.")
                .addDoc(
                    "<p>It is not possible to use",
                    KeepItemKind.ONLY_CLASS.name(),
                    "if annotating a member. Also, it is never valid to use kind",
                    KeepItemKind.ONLY_MEMBERS.name(),
                    "as the API surface must keep the class if any member it to be accessible.")
                .generate(this);
            println();
            generateMemberPropertiesNoBinding();
          });
      println();
      println("}");
    }

    private void generateUsedByX(String annotationClassName, String doc) {
      printCopyRight(2023);
      printPackage("annotations");
      printImports(ANNOTATION_IMPORTS);
      printDocString(
          "Annotation to mark a class, field or method as being " + doc + ".",
          "<p>Note: Before using this annotation, consider if instead you can annotate the code"
              + " that is doing reflection with {@link UsesReflection}. Annotating the reflecting"
              + " code is generally more clear and maintainable, and it also naturally gives rise"
              + " to edges that describe just the reflected aspects of the program. The {@link"
              + " UsedByReflection} annotation is suitable for cases where the reflecting code is"
              + " not under user control, or in migrating away from rules.",
          "<p>When a class is annotated, member patterns can be used to define which members are to"
              + " be kept. When no member patterns are specified the default pattern is to match"
              + " just the class.",
          "<p>When a member is annotated, the member patterns cannot be used as the annotated"
              + " member itself fully defines the item to be kept (i.e., itself).");
      println(
          "@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,"
              + " ElementType.CONSTRUCTOR})");
      println("@Retention(RetentionPolicy.CLASS)");
      println("public @interface " + annotationClassName + " {");
      println();
      withIndent(
          () -> {
            createDescriptionGroup().generate(this);
            println();
            createPreconditionsGroup().generate(this);
            println();
            createAdditionalTargetsGroup(
                    "Additional targets to be kept in addition to the annotated class/members.")
                .generate(this);
            println();
            GroupMember kindProperty = getKindMember();
            kindProperty
                .clearDocLines()
                .addDoc(
                    "<p>When annotating a class without member patterns, the default kind is "
                        + docLink(KeepItemKind.ONLY_CLASS)
                        + ".",
                    "<p>When annotating a class with member patterns, the default kind is "
                        + docLink(KeepItemKind.CLASS_AND_MEMBERS)
                        + ".",
                    "<p>When annotating a member, the default kind is "
                        + docLink(KeepItemKind.ONLY_MEMBERS)
                        + ".",
                    "<p>It is not possible to use ONLY_CLASS if annotating a member.")
                .generate(this);
            println();
            generateMemberPropertiesNoBinding();
          });
      println();
      println("}");
    }

    private String docLink(KeepItemKind kind) {
      return "{@link KeepItemKind#" + kind.name() + "}";
    }

    private void generateConstants() {
      printCopyRight(2023);
      printPackage("ast");
      printImports();
      printDocString(
          "Utility class for referencing the various keep annotations and their structure.",
          "<p>Use of these references avoids polluting the Java namespace with imports of the java "
              + "annotations which overlap in name with the actual semantic AST types.");
      println("public final class AnnotationConstants {");
      withIndent(
          () -> {
            // Root annotations.
            generateKeepEdgeConstants();
            generateKeepForApiConstants();
            generateUsesReflectionConstants();
            generateUsedByReflectionConstants();
            generateUsedByNativeConstants();
            generateCheckRemovedConstants();
            generateCheckOptimizedOutConstants();
            // Common item fields.
            generateItemConstants();
            // Inner annotation classes.
            generateBindingConstants();
            generateConditionConstants();
            generateTargetConstants();
            generateKindConstants();
            generateOptionConstants();
            generateMemberAccessConstants();
            generateMethodAccessConstants();
            generateFieldAccessConstants();
          });
      println("}");
    }

    private void generateAnnotationConstants(Class<?> clazz) {
      String name = simpleName(clazz);
      String desc = TestBase.descriptor(clazz);
      println("public static final String SIMPLE_NAME = " + quote(name) + ";");
      println("public static final String DESCRIPTOR = " + quote(desc) + ";");
    }

    private void generateKeepEdgeConstants() {
      println("public static final class Edge {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepEdge.class);
            createDescriptionGroup().generateConstants(this);
            createBindingsGroup().generateConstants(this);
            createPreconditionsGroup().generateConstants(this);
            createConsequencesGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateKeepForApiConstants() {
      println("public static final class ForApi {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepForApi.class);
            createDescriptionGroup().generateConstants(this);
            createAdditionalTargetsGroup(".").generateConstants(this);
            createMemberAccessGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateUsesReflectionConstants() {
      println("public static final class UsesReflection {");
      withIndent(
          () -> {
            generateAnnotationConstants(UsesReflection.class);
            createDescriptionGroup().generateConstants(this);
            createConsequencesAsValueGroup().generateConstants(this);
            createAdditionalPreconditionsGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateUsedByReflectionConstants() {
      println("public static final class UsedByReflection {");
      withIndent(
          () -> {
            generateAnnotationConstants(UsedByReflection.class);
            createDescriptionGroup().generateConstants(this);
            createPreconditionsGroup().generateConstants(this);
            createAdditionalTargetsGroup(".").generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateUsedByNativeConstants() {
      println("public static final class UsedByNative {");
      withIndent(
          () -> {
            generateAnnotationConstants(UsedByNative.class);
            println("// Content is the same as " + simpleName(UsedByReflection.class) + ".");
          });
      println("}");
      println();
    }

    private void generateCheckRemovedConstants() {
      println("public static final class CheckRemoved {");
      withIndent(
          () -> {
            generateAnnotationConstants(CheckRemoved.class);
          });
      println("}");
      println();
    }

    private void generateCheckOptimizedOutConstants() {
      println("public static final class CheckOptimizedOut {");
      withIndent(
          () -> {
            generateAnnotationConstants(CheckOptimizedOut.class);
          });
      println("}");
      println();
    }

    private void generateItemConstants() {
      printDocString("Item properties common to binding items, conditions and targets.");
      println("public static final class Item {");
      withIndent(
          () -> {
            // Bindings.
            createClassBindingGroup().generateConstants(this);
            createMemberBindingGroup().generateConstants(this);
            // Classes.
            createClassNamePatternGroup().generateConstants(this);
            createClassInstanceOfPatternGroup().generateConstants(this);
            // Members.
            createMemberAccessGroup().generateConstants(this);
            // Methods.
            createMethodAccessGroup().generateConstants(this);
            createMethodNameGroup().generateConstants(this);
            createMethodReturnTypeGroup().generateConstants(this);
            createMethodParametersGroup().generateConstants(this);
            // Fields.
            createFieldAccessGroup().generateConstants(this);
            createFieldNameGroup().generateConstants(this);
            createFieldTypeGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateBindingConstants() {
      println("public static final class Binding {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepBinding.class);
            bindingName().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateConditionConstants() {
      println("public static final class Condition {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepCondition.class);
          });
      println("}");
      println();
    }

    private void generateTargetConstants() {
      println("public static final class Target {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepTarget.class);
            getKindGroup().generateConstants(this);
            getKeepOptionsGroup().generateConstants(this);
          });
      println("}");
      println();
    }

    private void generateKindConstants() {
      println("public static final class Kind {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepItemKind.class);
            for (KeepItemKind value : KeepItemKind.values()) {
              if (value != KeepItemKind.DEFAULT) {
                println(
                    "public static final String "
                        + value.name()
                        + " = "
                        + quote(value.name())
                        + ";");
              }
            }
          });
      println("}");
      println();
    }

    private void generateOptionConstants() {
      println("public static final class Option {");
      withIndent(
          () -> {
            generateAnnotationConstants(KeepOption.class);
            for (KeepOption value : KeepOption.values()) {
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private boolean isMemberAccessProperty(String name) {
      for (MemberAccessFlags value : MemberAccessFlags.values()) {
        if (value.name().equals(name)) {
          return true;
        }
      }
      return false;
    }

    private void generateMemberAccessConstants() {
      println("public static final class MemberAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(MemberAccessFlags.class);
            println("public static final String NEGATION_PREFIX = \"NON_\";");
            for (MemberAccessFlags value : MemberAccessFlags.values()) {
              if (!value.name().startsWith("NON_")) {
                println(
                    "public static final String "
                        + value.name()
                        + " = "
                        + quote(value.name())
                        + ";");
              }
            }
          });
      println("}");
      println();
    }

    private void generateMethodAccessConstants() {
      println("public static final class MethodAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(MethodAccessFlags.class);
            for (MethodAccessFlags value : MethodAccessFlags.values()) {
              if (value.name().startsWith("NON_") || isMemberAccessProperty(value.name())) {
                continue;
              }
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private void generateFieldAccessConstants() {
      println("public static final class FieldAccess {");
      withIndent(
          () -> {
            generateAnnotationConstants(FieldAccessFlags.class);
            for (FieldAccessFlags value : FieldAccessFlags.values()) {
              if (value.name().startsWith("NON_") || isMemberAccessProperty(value.name())) {
                continue;
              }
              println(
                  "public static final String " + value.name() + " = " + quote(value.name()) + ";");
            }
          });
      println("}");
      println();
    }

    private static void writeFile(Path file, Consumer<Generator> fn) throws IOException {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteStream);
      Generator generator = new Generator(printStream);
      fn.accept(generator);
      String formatted = CodeGenerationBase.formatRawOutput(byteStream.toString());
      Files.write(Paths.get(ToolHelper.getProjectRoot()).resolve(file), formatted.getBytes());
    }

    public static void run() throws IOException {
      Path keepAnnoRoot = Paths.get("src/keepanno/java/com/android/tools/r8/keepanno");

      Path astPkg = keepAnnoRoot.resolve("ast");
      writeFile(astPkg.resolve("AnnotationConstants.java"), Generator::generateConstants);

      Path annoPkg = Paths.get("src/keepanno/java/com/android/tools/r8/keepanno/annotations");
      writeFile(annoPkg.resolve("KeepBinding.java"), Generator::generateKeepBinding);
      writeFile(annoPkg.resolve("KeepTarget.java"), Generator::generateKeepTarget);
      writeFile(annoPkg.resolve("KeepCondition.java"), Generator::generateKeepCondition);
      writeFile(annoPkg.resolve("KeepForApi.java"), Generator::generateKeepForApi);
      writeFile(
          annoPkg.resolve("UsedByReflection.java"),
          g -> g.generateUsedByX("UsedByReflection", "accessed reflectively"));
      writeFile(
          annoPkg.resolve("UsedByNative.java"),
          g -> g.generateUsedByX("UsedByNative", "accessed from native code via JNI"));
    }
  }
}
