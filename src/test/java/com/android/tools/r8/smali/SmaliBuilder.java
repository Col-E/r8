// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.smali;

import com.android.tools.r8.errors.DexOverflowException;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Smali;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.antlr.runtime.RecognitionException;

public class SmaliBuilder {

  public static class MethodSignature {

    public final String clazz;
    public final String name;
    public final String returnType;
    public final List<String> parameterTypes;

    public MethodSignature(String clazz, String name, String returnType,
        List<String> parameterTypes) {
      this.clazz = clazz;
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }

    public static MethodSignature staticInitializer(String clazz) {
      return new MethodSignature(clazz, "<clinit>", "void", ImmutableList.of());
    }

    @Override
    public String toString() {
      return returnType + " " + clazz + "." + name
          + "(" + StringUtils.join(parameterTypes, ",") + ")";
    }
  }

  abstract class Builder {

    String name;
    String superName;
    List<String> implementedInterfaces;
    String sourceFile = null;
    List<String> source = new ArrayList<>();

    Builder(String name, String superName, List<String> implementedInterfaces) {
      this.name = name;
      this.superName = superName;
      this.implementedInterfaces = implementedInterfaces;
    }

    protected void appendSuper(StringBuilder builder) {
      builder.append(".super ");
      builder.append(DescriptorUtils.javaTypeToDescriptor(superName));
      builder.append("\n");
    }

    protected void appendImplementedInterfaces(StringBuilder builder) {
      for (String implementedInterface : implementedInterfaces) {
        builder.append(".implements ");
        builder.append(DescriptorUtils.javaTypeToDescriptor(implementedInterface));
        builder.append("\n");
      }
    }

    protected void writeSource(StringBuilder builder) {
      for (String sourceLine : source) {
        builder.append(sourceLine);
        builder.append("\n");
      }
    }
  }

  public class ClassBuilder extends Builder {

    ClassBuilder(String name, String superName, List<String> implementedInterfaces) {
      super(name, superName, implementedInterfaces);
    }

    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append(".class public ");
      builder.append(DescriptorUtils.javaTypeToDescriptor(name));
      builder.append("\n");
      appendSuper(builder);
      appendImplementedInterfaces(builder);
      builder.append("\n");
      if (sourceFile != null) {
        builder.append(".source \"").append(sourceFile).append("\"\n");
      }
      writeSource(builder);
      return builder.toString();
    }
  }

  public class InterfaceBuilder extends Builder {

    InterfaceBuilder(String name, String superName) {
      super(name, superName, ImmutableList.of());
    }

    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append(".class public interface abstract ");
      builder.append(DescriptorUtils.javaTypeToDescriptor(name));
      builder.append("\n");
      appendSuper(builder);
      appendImplementedInterfaces(builder);
      builder.append("\n");
      writeSource(builder);
      return builder.toString();
    }
  }

  private String currentClassName;
  private final Map<String, Builder> classes = new HashMap<>();

  public SmaliBuilder() {
    // No default class.
  }

  public SmaliBuilder(String name) {
    addClass(name);
  }

  public SmaliBuilder(String name, String superName) {
    addClass(name, superName);
  }

  private List<String> getSource(String clazz) {
    return classes.get(clazz).source;
  }

  public String getCurrentClassName() {
    return currentClassName;
  }

  public String getCurrentClassDescriptor() {
    return DescriptorUtils.javaTypeToDescriptor(currentClassName);
  }

  public void addClass(String name) {
    addClass(name, "java.lang.Object");
  }

  public void addClass(String name, String superName) {
    addClass(name, superName, ImmutableList.of());
  }

  public void addClass(String name, String superName, List<String> implementedInterfaces) {
    assert !classes.containsKey(name);
    currentClassName = name;
    classes.put(name, new ClassBuilder(name, superName, implementedInterfaces));
  }

  public void addInterface(String name) {
    addInterface(name, "java.lang.Object");
  }

  public void addInterface(String name, String superName) {
    assert !classes.containsKey(name);
    currentClassName = name;
    classes.put(name, new InterfaceBuilder(name, superName));
  }

  public void setSourceFile(String file) {
    classes.get(currentClassName).sourceFile = file;
  }

  public void addDefaultConstructor() {
    String superDescriptor =
        DescriptorUtils.javaTypeToDescriptor(classes.get(currentClassName).superName);
    addMethodRaw(
        "  .method public constructor <init>()V",
        "    .locals 0",
        "    invoke-direct {p0}, " + superDescriptor + "-><init>()V",
        "    return-void",
        "  .end method"
    );
  }

  public void addStaticField(String name, String type, String defaultValue) {
    StringBuilder builder = new StringBuilder();
    builder.append(".field static ");
    builder.append(name);
    builder.append(":");
    builder.append(type);
    if (defaultValue != null) {
      builder.append(" = ");
      if (type.equals("Ljava/lang/String;")) {
        builder.append('"');
        builder.append(defaultValue);
        builder.append('"');
      } else {
        builder.append(defaultValue);
      }
    }
    getSource(currentClassName).add(builder.toString());
  }

  public void addStaticField(String name, String type) {
    addStaticField(name, type, null);
  }

  public void addInstanceField(String name, String type) {
    StringBuilder builder = new StringBuilder();
    builder.append(".field ");
    builder.append(name);
    builder.append(":");
    builder.append(type);
    getSource(currentClassName).add(builder.toString());
  }

  private MethodSignature addMethod(String flags, String returnType, String name,
      List<String> parameters, int locals, String code) {
    StringBuilder builder = new StringBuilder();
    builder.append(".method ");
    if (flags != null && flags.length() > 0) {
      builder.append(flags);
      builder.append(" ");
    }
    builder.append(name);
    builder.append("(");
    for (String parameter : parameters) {
      builder.append(DescriptorUtils.javaTypeToDescriptor(parameter));
    }
    builder.append(")");
    builder.append(DescriptorUtils.javaTypeToDescriptor(returnType));
    builder.append("\n");
    if (locals >= 0) {
      builder.append(".locals ");
      builder.append(locals);
      builder.append("\n\n");
      assert code != null;
      builder.append(code);
    } else {
      assert code == null;
    }
    builder.append(".end method");
    getSource(currentClassName).add(builder.toString());
    return new MethodSignature(currentClassName, name, returnType, parameters);
  }

  public MethodSignature addStaticMethod(
      String returnType, String name, List<String> parameters, int locals, String... instructions) {
    return addStaticMethod(returnType, name, parameters, locals, buildCode(instructions));
  }

  public MethodSignature addStaticMethod(
      String returnType, String name, List<String> parameters, int locals, String code) {
    return addStaticMethod("", returnType, name, parameters, locals, code);
  }

  public MethodSignature addStaticInitializer(int locals, String... instructions) {
    return addStaticInitializer(locals, buildCode(instructions));
  }

  public MethodSignature addStaticInitializer(int locals, String code) {
    return addStaticMethod("constructor", "void", "<clinit>", ImmutableList.of(), locals, code);
  }

  private MethodSignature addStaticMethod(
      String flags,
      String returnType,
      String name,
      List<String> parameters,
      int locals,
      String code) {
    return addMethod("public static " + flags, returnType, name, parameters, locals, code);
  }

  public MethodSignature addAbstractMethod(
      String returnType, String name, List<String> parameters) {
    return addMethod("public abstract", returnType, name, parameters, -1, null);
  }

  public MethodSignature addInitializer(
      List<String> parameters, int locals, String... instructions) {
    return addMethod(
        "public constructor", "void", "<init>", parameters, locals, buildCode(instructions));
  }

  public MethodSignature addInstanceMethod(
      String returnType, String name, List<String> parameters, int locals, String... instructions) {
    return addInstanceMethod(returnType, name, parameters, locals, buildCode(instructions));
  }

  public MethodSignature addInstanceMethod(
      String returnType, String name, List<String> parameters, int locals, String code) {
    return addMethod("public", returnType, name, parameters, locals, code);
  }

  public MethodSignature addMainMethod(int locals, String... instructions) {
    return addStaticMethod(
        "void", "main", Collections.singletonList("java.lang.String[]"), locals, instructions);
  }

  public void addMethodRaw(String... source) {
    StringBuilder builder = new StringBuilder();
    for (String line : source) {
      builder.append(line);
      builder.append(System.lineSeparator());
    }
    getSource(currentClassName).add(builder.toString());
  }

  private static String buildCode(String... instructions) {
    StringBuilder builder = new StringBuilder();
    for (String instruction : instructions) {
      builder.append(instruction);
      builder.append(System.lineSeparator());
    }
    return builder.toString();
  }

  public List<String> buildSource() {
    List<String> result = new ArrayList<>(classes.size());
    for (String clazz : classes.keySet()) {
      Builder classBuilder = classes.get(clazz);
      result.add(classBuilder.toString());
    }
    return result;
  }

  public byte[] compile()
      throws IOException, RecognitionException, DexOverflowException, ExecutionException {
    return Smali.compile(buildSource());
  }

  public AndroidApp build()
      throws IOException, RecognitionException, DexOverflowException, ExecutionException {
    return AndroidApp.builder().addDexProgramData(compile(), Origin.unknown()).build();
  }


  @Override
  public String toString() {
    return String.join("\n\n", buildSource());
  }
}
