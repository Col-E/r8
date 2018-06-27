// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.IgetBoolean;
import com.android.tools.r8.code.IgetByte;
import com.android.tools.r8.code.IgetChar;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.IgetShort;
import com.android.tools.r8.code.IgetWide;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeDirectRange;
import com.android.tools.r8.code.InvokeInterface;
import com.android.tools.r8.code.InvokeInterfaceRange;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeStaticRange;
import com.android.tools.r8.code.InvokeSuper;
import com.android.tools.r8.code.InvokeSuperRange;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.code.Iput;
import com.android.tools.r8.code.IputBoolean;
import com.android.tools.r8.code.IputByte;
import com.android.tools.r8.code.IputChar;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.IputShort;
import com.android.tools.r8.code.IputWide;
import com.android.tools.r8.code.Nop;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.code.SgetBoolean;
import com.android.tools.r8.code.SgetByte;
import com.android.tools.r8.code.SgetChar;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SgetShort;
import com.android.tools.r8.code.SgetWide;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputBoolean;
import com.android.tools.r8.code.SputByte;
import com.android.tools.r8.code.SputChar;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.code.SputShort;
import com.android.tools.r8.code.SputWide;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.signature.GenericSignatureAction;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.smali.SmaliBuilder;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class DexInspector {

  private final DexApplication application;
  private final DexItemFactory dexItemFactory;
  private final ClassNameMapper mapping;
  private final BiMap<String, String> originalToObfuscatedMapping;

  private final InstructionSubjectFactory factory = new InstructionSubjectFactory();

  public static MethodSignature MAIN =
      new MethodSignature("main", "void", new String[]{"java.lang.String[]"});

  public DexInspector(Path file, String mappingFile) throws IOException, ExecutionException {
    this(Collections.singletonList(file), mappingFile);
  }

  public DexInspector(Path file) throws IOException, ExecutionException {
    this(Collections.singletonList(file), null);
  }

  public DexInspector(List<Path> files) throws IOException, ExecutionException {
    this(files, null);
  }

  public DexInspector(List<Path> files, String mappingFile)
      throws IOException, ExecutionException {
    if (mappingFile != null) {
      this.mapping = ClassNameMapper.mapperFromFile(Paths.get(mappingFile));
      originalToObfuscatedMapping = this.mapping.getObfuscatedToOriginalMapping().inverse();
    } else {
      this.mapping = null;
      originalToObfuscatedMapping = null;
    }
    Timing timing = new Timing("DexInspector");
    InternalOptions options = new InternalOptions();
    dexItemFactory = options.itemFactory;
    AndroidApp input = AndroidApp.builder().addProgramFiles(files).build();
    application = new ApplicationReader(input, options, timing).read();
  }

  public DexInspector(AndroidApp app) throws IOException, ExecutionException {
    this(
        new ApplicationReader(app, new InternalOptions(), new Timing("DexInspector"))
            .read(app.getProguardMapOutputData()));
  }

  public DexInspector(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException, ExecutionException {
    this(
        new ApplicationReader(app, runOptionsConsumer(optionsConsumer), new Timing("DexInspector"))
            .read(app.getProguardMapOutputData()));
  }

  private static InternalOptions runOptionsConsumer(Consumer<InternalOptions> optionsConsumer) {
    InternalOptions internalOptions = new InternalOptions();
    optionsConsumer.accept(internalOptions);
    return internalOptions;
  }

  public DexInspector(AndroidApp app, Path proguardMap) throws IOException, ExecutionException {
    this(
        new ApplicationReader(app, new InternalOptions(), new Timing("DexInspector"))
            .read(StringResource.fromFile(proguardMap)));
  }

  public DexInspector(DexApplication application) {
    dexItemFactory = application.dexItemFactory;
    this.application = application;
    this.mapping = application.getProguardMap();
    originalToObfuscatedMapping =
        mapping == null ? null : mapping.getObfuscatedToOriginalMapping().inverse();
  }

  public DexItemFactory getFactory() {
    return dexItemFactory;
  }

  private DexType toDexType(String string) {
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(string));
  }

  private DexType toDexTypeIgnorePrimitives(String string) {
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptorIgnorePrimitives(string));
  }

  private static <S, T extends Subject> void forAll(S[] items,
      BiFunction<S, FoundClassSubject, ? extends T> constructor,
      FoundClassSubject clazz,
      Consumer<T> consumer) {
    for (S item : items) {
      consumer.accept(constructor.apply(item, clazz));
    }
  }

  private static <S, T extends Subject> void forAll(Iterable<S> items, Function<S, T> constructor,
      Consumer<T> consumer) {
    for (S item : items) {
      consumer.accept(constructor.apply(item));
    }
  }

  DexAnnotation findAnnotation(String name, DexAnnotationSet annotations) {
    for (DexAnnotation annotation : annotations.annotations) {
      DexType type = annotation.annotation.type;
      String original = mapping == null ? type.toSourceString() : mapping.originalNameOf(type);
      if (original.equals(name)) {
        return annotation;
      }
    }
    return null;
  }

  public String getFinalSignatureAttribute(DexAnnotationSet annotations) {
    DexAnnotation annotation =
        findAnnotation("dalvik.annotation.Signature", annotations);
    if (annotation == null) {
      return null;
    }
    assert annotation.annotation.elements.length == 1;
    DexAnnotationElement element = annotation.annotation.elements[0];
    assert element.value instanceof DexValueArray;
    StringBuilder builder = new StringBuilder();
    DexValueArray valueArray = (DexValueArray) element.value;
    for (DexValue value : valueArray.getValues()) {
      assertTrue(value instanceof DexValueString);
      DexValueString s = (DexValueString) value;
      builder.append(s.getValue());
    }
    return builder.toString();
  }

  public String getOriginalSignatureAttribute(
      DexAnnotationSet annotations, BiConsumer<GenericSignatureParser, String> parse) {
    String finalSignature = getFinalSignatureAttribute(annotations);
    if (finalSignature == null || mapping == null) {
      return finalSignature;
    }

    GenericSignatureGenerater rewriter = new GenericSignatureGenerater();
    GenericSignatureParser<String> parser = new GenericSignatureParser<>(rewriter);
    parse.accept(parser, finalSignature);
    return rewriter.getSignature();
  }


  public ClassSubject clazz(Class clazz) {
    return clazz(clazz.getTypeName());
  }

  /**
   * Lookup a class by name. This allows both original and obfuscated names.
   */
  public ClassSubject clazz(String name) {
    ClassNamingForNameMapper naming = null;
    if (mapping != null) {
      String obfuscated = originalToObfuscatedMapping.get(name);
      if (obfuscated != null) {
        naming = mapping.getClassNaming(obfuscated);
        name = obfuscated;
      } else {
        // Figure out if the name is an already obfuscated name.
        String original = originalToObfuscatedMapping.inverse().get(name);
        if (original != null) {
          naming = mapping.getClassNaming(name);
        }
      }
    }
    DexClass clazz = application.definitionFor(toDexTypeIgnorePrimitives(name));
    if (clazz == null) {
      return new AbsentClassSubject();
    }
    return new FoundClassSubject(clazz, naming);
  }

  public void forAllClasses(Consumer<FoundClassSubject> inspection) {
    forAll(application.classes(), cls -> {
      ClassSubject subject = clazz(cls.type.toSourceString());
      assert subject.isPresent();
      return (FoundClassSubject) subject;
    }, inspection);
  }

  public List<FoundClassSubject> allClasses() {
    ImmutableList.Builder<FoundClassSubject> builder = ImmutableList.builder();
    forAllClasses(builder::add);
    return builder.build();
  }

  public MethodSubject method(Method method) {
    ClassSubject clazz = clazz(method.getDeclaringClass());
    if (!clazz.isPresent()) {
      return new AbsentMethodSubject();
    }
    return clazz.method(method);
  }

  private String getObfuscatedTypeName(String originalTypeName) {
    String obfuscatedType = null;
    if (mapping != null) {
      obfuscatedType = originalToObfuscatedMapping.get(originalTypeName);
    }
    obfuscatedType = obfuscatedType == null ? originalTypeName : obfuscatedType;
    return obfuscatedType;
  }

  public abstract class Subject {

    public abstract boolean isPresent();
    public abstract boolean isRenamed();
  }

  public abstract class AnnotationSubject extends Subject {

    public abstract DexEncodedAnnotation getAnnotation();
  }

  public class FoundAnnotationSubject extends AnnotationSubject {

    private final DexAnnotation annotation;

    private FoundAnnotationSubject(DexAnnotation annotation) {
      this.annotation = annotation;
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    @Override
    public DexEncodedAnnotation getAnnotation() {
      return annotation.annotation;
    }
  }

  public class AbsentAnnotationSubject extends AnnotationSubject {

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    @Override
    public DexEncodedAnnotation getAnnotation() {
      throw new UnsupportedOperationException();
    }
  }


  public abstract class ClassSubject extends Subject {

    public abstract void forAllMethods(Consumer<FoundMethodSubject> inspection);

    public MethodSubject method(Method method) {
      List<String> parameters = new ArrayList<>();
      for (Class<?> parameterType : method.getParameterTypes()) {
        parameters.add(parameterType.getTypeName());
      }
      return method(method.getReturnType().getTypeName(), method.getName(), parameters);
    }

    public abstract MethodSubject method(String returnType, String name, List<String> parameters);

    public MethodSubject clinit() {
      return method("void", "<clinit>", ImmutableList.of());
    }

    public MethodSubject init(List<String> parameters) {
      return method("void", "<init>", parameters);
    }

    public MethodSubject method(MethodSignature signature) {
      return method(signature.type, signature.name, ImmutableList.copyOf(signature.parameters));
    }

    public MethodSubject method(SmaliBuilder.MethodSignature signature) {
      return method(
          signature.returnType, signature.name, ImmutableList.copyOf(signature.parameterTypes));
    }

    public abstract void forAllFields(Consumer<FoundFieldSubject> inspection);

    public abstract FieldSubject field(String type, String name);

    public abstract boolean isAbstract();

    public abstract boolean isAnnotation();

    public String dumpMethods() {
      StringBuilder dump = new StringBuilder();
      forAllMethods((FoundMethodSubject method) ->
          dump.append(method.getMethod().toString())
              .append(method.getMethod().codeToString()));
      return dump.toString();
    }

    public abstract DexClass getDexClass();

    public abstract AnnotationSubject annotation(String name);

    public abstract String getOriginalName();

    public abstract String getOriginalDescriptor();

    public abstract String getFinalDescriptor();

    public abstract boolean isMemberClass();

    public abstract boolean isLocalClass();

    public abstract boolean isAnonymousClass();

    public abstract String getOriginalSignatureAttribute();

    public abstract String getFinalSignatureAttribute();
  }

  private class AbsentClassSubject extends ClassSubject {

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public void forAllMethods(Consumer<FoundMethodSubject> inspection) {
    }

    @Override
    public MethodSubject method(String returnType, String name, List<String> parameters) {
      return new AbsentMethodSubject();
    }

    @Override
    public void forAllFields(Consumer<FoundFieldSubject> inspection) {
    }

    @Override
    public FieldSubject field(String type, String name) {
      return new AbsentFieldSubject();
    }

    @Override
    public boolean isAbstract() {
      return false;
    }

    @Override
    public boolean isAnnotation() {
      return false;
    }

    @Override
    public DexClass getDexClass() {
      return null;
    }

    @Override
    public AnnotationSubject annotation(String name) {
      return new AbsentAnnotationSubject();
    }

    @Override
    public String getOriginalName() {
      return null;
    }

    @Override
    public String getOriginalDescriptor() {
      return null;
    }

    @Override
    public String getFinalDescriptor() {
      return null;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    @Override
    public boolean isMemberClass() {
      return false;
    }

    @Override
    public boolean isLocalClass() {
      return false;
    }

    @Override
    public boolean isAnonymousClass() {
      return false;
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return null;
    }

    @Override
    public String getFinalSignatureAttribute() {
      return null;
    }
  }

  public class FoundClassSubject extends ClassSubject {

    private final DexClass dexClass;
    private final ClassNamingForNameMapper naming;

    private FoundClassSubject(DexClass dexClass, ClassNamingForNameMapper naming) {
      this.dexClass = dexClass;
      this.naming = naming;
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public void forAllMethods(Consumer<FoundMethodSubject> inspection) {
      forAll(dexClass.directMethods(), FoundMethodSubject::new, this, inspection);
      forAll(dexClass.virtualMethods(), FoundMethodSubject::new, this, inspection);
    }

    @Override
    public MethodSubject method(String returnType, String name, List<String> parameters) {
      DexType[] parameterTypes = new DexType[parameters.size()];
      for (int i = 0; i < parameters.size(); i++) {
        parameterTypes[i] = toDexType(getObfuscatedTypeName(parameters.get(i)));
      }
      DexProto proto = dexItemFactory.createProto(toDexType(getObfuscatedTypeName(returnType)),
          parameterTypes);
      if (naming != null) {
        String[] parameterStrings = new String[parameterTypes.length];
        Signature signature = new MethodSignature(name, returnType,
            parameters.toArray(parameterStrings));
        MemberNaming methodNaming = naming.lookupByOriginalSignature(signature);
        if (methodNaming != null) {
          name = methodNaming.getRenamedName();
        }
      }
      DexMethod dexMethod =
          dexItemFactory.createMethod(dexClass.type, proto, dexItemFactory.createString(name));
      DexEncodedMethod encoded = findMethod(dexClass.directMethods(), dexMethod);
      if (encoded == null) {
        encoded = findMethod(dexClass.virtualMethods(), dexMethod);
      }
      return encoded == null ? new AbsentMethodSubject() : new FoundMethodSubject(encoded, this);
    }

    private DexEncodedMethod findMethod(DexEncodedMethod[] methods, DexMethod dexMethod) {
      for (DexEncodedMethod method : methods) {
        if (method.method.equals(dexMethod)) {
          return method;
        }
      }
      return null;
    }

    @Override
    public void forAllFields(Consumer<FoundFieldSubject> inspection) {
      forAll(dexClass.staticFields(), FoundFieldSubject::new, this, inspection);
      forAll(dexClass.instanceFields(), FoundFieldSubject::new, this, inspection);
    }

    @Override
    public FieldSubject field(String type, String name) {
      String obfuscatedType = getObfuscatedTypeName(type);
      MemberNaming fieldNaming = null;
      if (naming != null) {
        fieldNaming = naming.lookupByOriginalSignature(
            new FieldSignature(name, type));
      }
      String obfuscatedName = fieldNaming == null ? name : fieldNaming.getRenamedName();

      DexField field = dexItemFactory.createField(dexClass.type,
          toDexType(obfuscatedType), dexItemFactory.createString(obfuscatedName));
      DexEncodedField encoded = findField(dexClass.staticFields(), field);
      if (encoded == null) {
        encoded = findField(dexClass.instanceFields(), field);
      }
      return encoded == null ? new AbsentFieldSubject() : new FoundFieldSubject(encoded, this);
    }

    @Override
    public boolean isAbstract() {
      return dexClass.accessFlags.isAbstract();
    }

    @Override
    public boolean isAnnotation() {
      return dexClass.accessFlags.isAnnotation();
    }

    private DexEncodedField findField(DexEncodedField[] fields, DexField dexField) {
      for (DexEncodedField field : fields) {
        if (field.field.equals(dexField)) {
          return field;
        }
      }
      return null;
    }

    @Override
    public DexClass getDexClass() {
      return dexClass;
    }

    @Override
    public AnnotationSubject annotation(String name) {
      // Ensure we don't check for annotations represented as attributes.
      assert !name.endsWith("EnclosingClass")
          && !name.endsWith("EnclosingMethod")
          && !name.endsWith("InnerClass");
      DexAnnotation annotation = findAnnotation(name, dexClass.annotations);
      return annotation == null
          ? new AbsentAnnotationSubject()
          : new FoundAnnotationSubject(annotation);
    }

    @Override
    public String getOriginalName() {
      if (naming != null) {
        return naming.originalName;
      } else {
        return DescriptorUtils.descriptorToJavaType(getFinalDescriptor());
      }
    }

    @Override
    public String getOriginalDescriptor() {
      if (naming != null) {
        return DescriptorUtils.javaTypeToDescriptor(naming.originalName);
      } else {
        return getFinalDescriptor();
      }
    }

    @Override
    public String getFinalDescriptor() {
      return dexClass.type.descriptor.toString();
    }

    @Override
    public boolean isRenamed() {
      return naming != null && !getFinalDescriptor().equals(getOriginalDescriptor());
    }

    private InnerClassAttribute getInnerClassAttribute() {
      for (InnerClassAttribute innerClassAttribute : dexClass.getInnerClasses()) {
        if (dexClass.type == innerClassAttribute.getInner()) {
          return innerClassAttribute;
        }
      }
      return null;
    }

    @Override
    public boolean isLocalClass() {
      InnerClassAttribute innerClass = getInnerClassAttribute();
      return innerClass != null
          && innerClass.isNamed()
          && dexClass.getEnclosingMethod() != null;
    }

    @Override
    public boolean isMemberClass() {
      InnerClassAttribute innerClass = getInnerClassAttribute();
      return innerClass != null
          && innerClass.getOuter() != null
          && innerClass.isNamed()
          && dexClass.getEnclosingMethod() == null;
    }

    @Override
    public boolean isAnonymousClass() {
      InnerClassAttribute innerClass = getInnerClassAttribute();
      return innerClass != null
          && innerClass.isAnonymous()
          && dexClass.getEnclosingMethod() != null;
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return DexInspector.this.getOriginalSignatureAttribute(
          dexClass.annotations, GenericSignatureParser::parseClassSignature);
    }

    @Override
    public String getFinalSignatureAttribute() {
      return DexInspector.this.getFinalSignatureAttribute(dexClass.annotations);
    }

    @Override
    public String toString() {
      return dexClass.toSourceString();
    }
  }

  public abstract class MemberSubject extends Subject {

    public abstract boolean isPublic();

    public abstract boolean isStatic();

    public abstract boolean isFinal();

    public abstract Signature getOriginalSignature();

    public abstract Signature getFinalSignature();

    public String getOriginalName() {
      Signature originalSignature = getOriginalSignature();
      return originalSignature == null ? null : originalSignature.name;
    }

    public String getFinalName() {
      Signature finalSignature = getFinalSignature();
      return finalSignature == null ? null : finalSignature.name;
    }
  }

  public abstract class MethodSubject extends MemberSubject {

    public abstract boolean isAbstract();

    public abstract boolean isBridge();

    public abstract boolean isInstanceInitializer();

    public abstract boolean isClassInitializer();

    public abstract String getOriginalSignatureAttribute();

    public abstract String getFinalSignatureAttribute();

    public abstract DexEncodedMethod getMethod();

    public Iterator<InstructionSubject> iterateInstructions() {
      return null;
    }

    public <T extends InstructionSubject> Iterator<T> iterateInstructions(
        Predicate<InstructionSubject> filter) {
      return null;
    }
  }

  public class AbsentMethodSubject extends MethodSubject {

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    @Override
    public boolean isPublic() {
      return false;
    }

    @Override
    public boolean isStatic() {
      return false;
    }

    @Override
    public boolean isFinal() {
      return false;
    }

    @Override
    public boolean isAbstract() {
      return false;
    }

    @Override
    public boolean isBridge() {
      return false;
    }

    @Override
    public boolean isInstanceInitializer() {
      return false;
    }

    @Override
    public boolean isClassInitializer() {
      return false;
    }

    @Override
    public DexEncodedMethod getMethod() {
      return null;
    }

    @Override
    public Signature getOriginalSignature() {
      return null;
    }

    @Override
    public Signature getFinalSignature() {
      return null;
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return null;
    }

    @Override
    public String getFinalSignatureAttribute() {
      return null;
    }
  }

  public class FoundMethodSubject extends MethodSubject {

    private final FoundClassSubject clazz;
    private final DexEncodedMethod dexMethod;

    public FoundMethodSubject(DexEncodedMethod encoded, FoundClassSubject clazz) {
      this.clazz = clazz;
      this.dexMethod = encoded;
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isRenamed() {
      return clazz.naming != null && !getFinalSignature().name.equals(getOriginalSignature().name);
    }

    @Override
    public boolean isPublic() {
      return dexMethod.accessFlags.isPublic();
    }

    @Override
    public boolean isStatic() {
      return dexMethod.accessFlags.isStatic();
    }

    @Override
    public boolean isFinal() {
      return dexMethod.accessFlags.isFinal();
    }

    @Override
    public boolean isAbstract() {
      return dexMethod.accessFlags.isAbstract();
    }

    @Override
    public boolean isBridge() {
      return dexMethod.accessFlags.isBridge();
    }

    @Override
    public boolean isInstanceInitializer() {
      return dexMethod.isInstanceInitializer();
    }

    @Override
    public boolean isClassInitializer() {
      return dexMethod.isClassInitializer();
    }

    @Override
    public DexEncodedMethod getMethod() {
      return dexMethod;
    }

    @Override
    public MethodSignature getOriginalSignature() {
      MethodSignature signature = getFinalSignature();
      if (clazz.naming == null) {
        return signature;
      }

      // Map the parameters and return type to original names. This is needed as the in the
      // Proguard map the names on the left side are the original names. E.g.
      //
      //   X -> a
      //     X method(X) -> a
      //
      // whereas the final signature is for X.a is "a (a)"
      String[] OriginalParameters = new String[signature.parameters.length];
      for (int i = 0; i < OriginalParameters.length; i++) {
        String obfuscated = signature.parameters[i];
        String original = originalToObfuscatedMapping.inverse().get(obfuscated);
        OriginalParameters[i] = original != null ? original : obfuscated;
      }
      String obfuscatedReturnType = signature.type;
      String originalReturnType = originalToObfuscatedMapping.inverse().get(obfuscatedReturnType);
      String returnType = originalReturnType != null ? originalReturnType : obfuscatedReturnType;

      MethodSignature lookupSignature =
          new MethodSignature(signature.name, returnType, OriginalParameters);

      MemberNaming memberNaming = clazz.naming.lookup(lookupSignature);
      return memberNaming != null
          ? (MethodSignature) memberNaming.getOriginalSignature()
          : signature;
    }

    @Override
    public MethodSignature getFinalSignature() {
      return MemberNaming.MethodSignature.fromDexMethod(dexMethod.method);
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return DexInspector.this.getOriginalSignatureAttribute(
          dexMethod.annotations, GenericSignatureParser::parseMethodSignature);
    }

    @Override
    public String getFinalSignatureAttribute() {
      return DexInspector.this.getFinalSignatureAttribute(dexMethod.annotations);
    }

    @Override
    public Iterator<InstructionSubject> iterateInstructions() {
      return new InstructionIterator(this);
    }

    @Override
    public <T extends InstructionSubject> Iterator<T> iterateInstructions(
        Predicate<InstructionSubject> filter) {
      return new FilteredInstructionIterator<>(this, filter);
    }

    @Override
    public String toString() {
      return dexMethod.toSourceString();
    }
  }

  public abstract class FieldSubject extends MemberSubject {
    public abstract boolean hasExplicitStaticValue();

    public abstract DexEncodedField getField();

    public abstract DexValue getStaticValue();

    public abstract boolean isRenamed();

    public abstract String getOriginalSignatureAttribute();

    public abstract String getFinalSignatureAttribute();
  }

  public class AbsentFieldSubject extends FieldSubject {

    @Override
    public boolean isPublic() {
      return false;
    }

    @Override
    public boolean isStatic() {
      return false;
    }

    @Override
    public boolean isFinal() {
      return false;
    }

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    @Override
    public Signature getOriginalSignature() {
      return null;
    }

    @Override
    public Signature getFinalSignature() {
      return null;
    }

    @Override
    public boolean hasExplicitStaticValue() {
      return false;
    }

    @Override
    public DexValue getStaticValue() {
      return null;
    }

    @Override
    public DexEncodedField getField() {
      return null;
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return null;
    }

    @Override
    public String getFinalSignatureAttribute() {
      return null;
    }
  }

  public class FoundFieldSubject extends FieldSubject {

    private final FoundClassSubject clazz;
    private final DexEncodedField dexField;

    public FoundFieldSubject(DexEncodedField dexField, FoundClassSubject clazz) {
      this.clazz = clazz;
      this.dexField = dexField;
    }

    @Override
    public boolean isPublic() {
      return dexField.accessFlags.isPublic();
    }

    @Override
    public boolean isStatic() {
      return dexField.accessFlags.isStatic();
    }

    @Override
    public boolean isFinal() {
      return dexField.accessFlags.isFinal();
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isRenamed() {
      return clazz.naming != null && !getFinalSignature().name.equals(getOriginalSignature().name);
    }


    public TypeSubject type() {
      return new TypeSubject(dexField.field.type);
    }

    @Override
    public FieldSignature getOriginalSignature() {
      FieldSignature signature = getFinalSignature();
      if (clazz.naming == null) {
        return signature;
      }

      // Map the type to the original name. This is needed as the in the Proguard map the
      // names on the left side are the original names. E.g.
      //
      //   X -> a
      //     X field -> a
      //
      // whereas the final signature is for X.a is "a a"
      String obfuscatedType = signature.type;
      String originalType = originalToObfuscatedMapping.inverse().get(obfuscatedType);
      String fieldType = originalType != null ? originalType : obfuscatedType;

      FieldSignature lookupSignature = new FieldSignature(signature.name, fieldType);

      MemberNaming memberNaming = clazz.naming.lookup(lookupSignature);
      return memberNaming != null
          ? (FieldSignature) memberNaming.getOriginalSignature()
          : signature;
    }

    @Override
    public FieldSignature getFinalSignature() {
      return MemberNaming.FieldSignature.fromDexField(dexField.field);
    }

    @Override
    public boolean hasExplicitStaticValue() {
      return isStatic() && dexField.hasExplicitStaticValue();
    }

    @Override
    public DexValue getStaticValue() {
      return dexField.getStaticValue();
    }

    @Override
    public DexEncodedField getField() {
      return dexField;
    }

    @Override
    public String getOriginalSignatureAttribute() {
      return DexInspector.this.getOriginalSignatureAttribute(
          dexField.annotations, GenericSignatureParser::parseFieldSignature);
    }

    @Override
    public String getFinalSignatureAttribute() {
      return DexInspector.this.getFinalSignatureAttribute(dexField.annotations);
    }

    @Override
    public String toString() {
      return dexField.toSourceString();
    }
  }

  public class TypeSubject extends Subject {

    private final DexType dexType;

    TypeSubject(DexType dexType) {
      this.dexType = dexType;
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public boolean isRenamed() {
      return false;
    }

    public boolean is(String type) {
      return dexType.equals(toDexType(type));
    }

    public String toString() {
      return dexType.toSourceString();
    }
  }

  private class InstructionSubjectFactory {

    InstructionSubject create(Instruction instruction) {
      if (isInvoke(instruction)) {
        return new InvokeInstructionSubject(this, instruction);
      } else if (isFieldAccess(instruction)) {
        return new FieldAccessInstructionSubject(this, instruction);
      } else {
        return new InstructionSubject(this, instruction);
      }
    }

    boolean isInvoke(Instruction instruction) {
      return isInvokeVirtual(instruction)
          || isInvokeInterface(instruction)
          || isInvokeDirect(instruction)
          || isInvokeSuper(instruction)
          || isInvokeStatic(instruction);
    }

    boolean isInvokeVirtual(Instruction instruction) {
      return instruction instanceof InvokeVirtual || instruction instanceof InvokeVirtualRange;
    }

    boolean isInvokeInterface(Instruction instruction) {
      return instruction instanceof InvokeInterface || instruction instanceof InvokeInterfaceRange;
    }

    boolean isInvokeDirect(Instruction instruction) {
      return instruction instanceof InvokeDirect || instruction instanceof InvokeDirectRange;
    }

    boolean isInvokeSuper(Instruction instruction) {
      return instruction instanceof InvokeSuper || instruction instanceof InvokeSuperRange;
    }

    boolean isInvokeStatic(Instruction instruction) {
      return instruction instanceof InvokeStatic || instruction instanceof InvokeStaticRange;
    }

    boolean isNop(Instruction instruction) {
      return instruction instanceof Nop;
    }

    boolean isGoto(Instruction instruction) {
      return instruction instanceof Goto;
    }

    boolean isReturnVoid(Instruction instruction) {
      return instruction instanceof ReturnVoid;
    }

    boolean isConst4(Instruction instruction) {
      return instruction instanceof Const4;
    }

    boolean isThrow(Instruction instruction) {
      return instruction instanceof Throw;
    }

    boolean isConstString(Instruction instruction) {
      return instruction instanceof ConstString;
    }

    boolean isConstString(Instruction instruction, String value) {
      return instruction instanceof ConstString
          && ((ConstString) instruction).BBBB.toSourceString().equals(value);
    }

    boolean isIfNez(Instruction instruction) {
      return instruction instanceof IfNez;
    }

    boolean isIfEqz(Instruction instruction) {
      return instruction instanceof IfEqz;
    }

    boolean isFieldAccess(Instruction instruction) {
      return isInstanceGet(instruction)
          || isInstancePut(instruction)
          || isStaticGet(instruction)
          || isStaticSet(instruction);
    }

    boolean isInstanceGet(Instruction instruction) {
      return instruction instanceof Iget
          || instruction instanceof IgetBoolean
          || instruction instanceof IgetByte
          || instruction instanceof IgetShort
          || instruction instanceof IgetChar
          || instruction instanceof IgetWide
          || instruction instanceof IgetObject;
    }

    boolean isInstancePut(Instruction instruction) {
      return instruction instanceof Iput
          || instruction instanceof IputBoolean
          || instruction instanceof IputByte
          || instruction instanceof IputShort
          || instruction instanceof IputChar
          || instruction instanceof IputWide
          || instruction instanceof IputObject;
    }

    boolean isStaticGet(Instruction instruction) {
      return instruction instanceof Sget
          || instruction instanceof SgetBoolean
          || instruction instanceof SgetByte
          || instruction instanceof SgetShort
          || instruction instanceof SgetChar
          || instruction instanceof SgetWide
          || instruction instanceof SgetObject;
    }

    boolean isStaticSet(Instruction instruction) {
      return instruction instanceof Sput
          || instruction instanceof SputBoolean
          || instruction instanceof SputByte
          || instruction instanceof SputShort
          || instruction instanceof SputChar
          || instruction instanceof SputWide
          || instruction instanceof SputObject;
    }
  }

  public class InstructionSubject {

    protected final InstructionSubjectFactory factory;
    protected final Instruction instruction;

    protected InstructionSubject(InstructionSubjectFactory factory, Instruction instruction) {
      this.factory = factory;
      this.instruction = instruction;
    }

    public boolean isInvoke() {
      return factory.isInvoke(instruction);
    }

    public boolean isFieldAccess() {
      return factory.isFieldAccess(instruction);
    }

    public boolean isInvokeVirtual() {
      return factory.isInvokeVirtual(instruction);
    }

    public boolean isInvokeInterface() {
      return factory.isInvokeInterface(instruction);
    }

    public boolean isInvokeDirect() {
      return factory.isInvokeDirect(instruction);
    }

    public boolean isInvokeSuper() {
      return factory.isInvokeSuper(instruction);
    }

    public boolean isInvokeStatic() {
      return factory.isInvokeStatic(instruction);
    }

    boolean isFieldAccess(Instruction instruction) {
      return factory.isFieldAccess(instruction);
    }

    public boolean isNop() {
      return factory.isNop(instruction);
    }

    public boolean isConstString() {
      return factory.isConstString(instruction);
    }

    public boolean isConstString(String value) {
      return factory.isConstString(instruction, value);
    }

    public boolean isGoto() {
      return factory.isGoto(instruction);
    }

    public boolean isIfNez() {
      return factory.isIfNez(instruction);
    }

    public boolean isIfEqz() {
      return factory.isIfEqz(instruction);
    }

    public boolean isReturnVoid() {
      return factory.isReturnVoid(instruction);
    }

    public boolean isConst4() {
      return factory.isConst4(instruction);
    }

    public boolean isThrow() {
      return factory.isThrow(instruction);
    }
  }

  public class InvokeInstructionSubject extends InstructionSubject {

    InvokeInstructionSubject(InstructionSubjectFactory factory, Instruction instruction) {
      super(factory, instruction);
      assert isInvoke();
    }

    public TypeSubject holder() {
      return new TypeSubject(invokedMethod().getHolder());
    }

    public DexMethod invokedMethod() {
      if (instruction instanceof InvokeVirtual) {
        return ((InvokeVirtual) instruction).getMethod();
      }
      if (instruction instanceof InvokeVirtualRange) {
        return ((InvokeVirtualRange) instruction).getMethod();
      }
      if (instruction instanceof InvokeInterface) {
        return ((InvokeInterface) instruction).getMethod();
      }
      if (instruction instanceof InvokeInterfaceRange) {
        return ((InvokeInterfaceRange) instruction).getMethod();
      }
      if (instruction instanceof InvokeDirect) {
        return ((InvokeDirect) instruction).getMethod();
      }
      if (instruction instanceof InvokeDirectRange) {
        return ((InvokeDirectRange) instruction).getMethod();
      }
      if (instruction instanceof InvokeSuper) {
        return ((InvokeSuper) instruction).getMethod();
      }
      if (instruction instanceof InvokeSuperRange) {
        return ((InvokeSuperRange) instruction).getMethod();
      }
      if (instruction instanceof InvokeDirect) {
        return ((InvokeDirect) instruction).getMethod();
      }
      if (instruction instanceof InvokeDirectRange) {
        return ((InvokeDirectRange) instruction).getMethod();
      }
      if (instruction instanceof InvokeStatic) {
        return ((InvokeStatic) instruction).getMethod();
      }
      if (instruction instanceof InvokeStaticRange) {
        return ((InvokeStaticRange) instruction).getMethod();
      }
      assert false;
      return null;
    }
  }

  public class FieldAccessInstructionSubject extends InstructionSubject {

    FieldAccessInstructionSubject(InstructionSubjectFactory factory, Instruction instruction) {
      super(factory, instruction);
      assert isFieldAccess();
    }

    public TypeSubject holder() {
      return new TypeSubject(accessedField().getHolder());
    }

    public DexField accessedField() {
      if (instruction instanceof Iget) {
        return ((Iget) instruction).getField();
      }
      if (instruction instanceof IgetBoolean) {
        return ((IgetBoolean) instruction).getField();
      }
      if (instruction instanceof IgetByte) {
        return ((IgetByte) instruction).getField();
      }
      if (instruction instanceof IgetShort) {
        return ((IgetShort) instruction).getField();
      }
      if (instruction instanceof IgetChar) {
        return ((IgetChar) instruction).getField();
      }
      if (instruction instanceof IgetWide) {
        return ((IgetWide) instruction).getField();
      }
      if (instruction instanceof IgetObject) {
        return ((IgetObject) instruction).getField();
      }
      if (instruction instanceof Iput) {
        return ((Iput) instruction).getField();
      }
      if (instruction instanceof IputBoolean) {
        return ((IputBoolean) instruction).getField();
      }
      if (instruction instanceof IputByte) {
        return ((IputByte) instruction).getField();
      }
      if (instruction instanceof IputShort) {
        return ((IputShort) instruction).getField();
      }
      if (instruction instanceof IputChar) {
        return ((IputChar) instruction).getField();
      }
      if (instruction instanceof IputWide) {
        return ((IputWide) instruction).getField();
      }
      if (instruction instanceof IputObject) {
        return ((IputObject) instruction).getField();
      }
      if (instruction instanceof Sget) {
        return ((Sget) instruction).getField();
      }
      if (instruction instanceof SgetBoolean) {
        return ((SgetBoolean) instruction).getField();
      }
      if (instruction instanceof SgetByte) {
        return ((SgetByte) instruction).getField();
      }
      if (instruction instanceof SgetShort) {
        return ((SgetShort) instruction).getField();
      }
      if (instruction instanceof SgetChar) {
        return ((SgetChar) instruction).getField();
      }
      if (instruction instanceof SgetWide) {
        return ((SgetWide) instruction).getField();
      }
      if (instruction instanceof SgetObject) {
        return ((SgetObject) instruction).getField();
      }
      if (instruction instanceof Sput) {
        return ((Sput) instruction).getField();
      }
      if (instruction instanceof SputBoolean) {
        return ((SputBoolean) instruction).getField();
      }
      if (instruction instanceof SputByte) {
        return ((SputByte) instruction).getField();
      }
      if (instruction instanceof SputShort) {
        return ((SputShort) instruction).getField();
      }
      if (instruction instanceof SputChar) {
        return ((SputChar) instruction).getField();
      }
      if (instruction instanceof SputWide) {
        return ((SputWide) instruction).getField();
      }
      if (instruction instanceof SputObject) {
        return ((SputObject) instruction).getField();
      }
      assert false;
      return null;
    }
  }

  private class InstructionIterator implements Iterator<InstructionSubject> {

    private final DexCode code;
    private int index;

    InstructionIterator(MethodSubject method) {
      assert method.isPresent();
      this.code = method.getMethod().getCode().asDexCode();
      this.index = 0;
    }

    @Override
    public boolean hasNext() {
      return index < code.instructions.length;
    }

    @Override
    public InstructionSubject next() {
      if (index == code.instructions.length) {
        throw new NoSuchElementException();
      }
      return factory.create(code.instructions[index++]);
    }
  }

  private class FilteredInstructionIterator<T extends InstructionSubject> implements Iterator<T> {

    private final InstructionIterator iterator;
    private final Predicate<InstructionSubject> predicate;
    private InstructionSubject pendingNext = null;

    FilteredInstructionIterator(MethodSubject method, Predicate<InstructionSubject> predicate) {
      this.iterator = new InstructionIterator(method);
      this.predicate = predicate;
      hasNext();
    }

    @Override
    public boolean hasNext() {
      if (pendingNext == null) {
        while (iterator.hasNext()) {
          pendingNext = iterator.next();
          if (predicate.test(pendingNext)) {
            break;
          }
          pendingNext = null;
        }
      }
      return pendingNext != null;
    }

    @Override
    public T next() {
      hasNext();
      if (pendingNext == null) {
        throw new NoSuchElementException();
      }
      // We cannot tell if the provided predicate will only match instruction subjects of type T.
      @SuppressWarnings("unchecked")
      T result = (T) pendingNext;
      pendingNext = null;
      return result;
    }
  }

  // Build the generic signature using the current mapping if any.
  class GenericSignatureGenerater implements GenericSignatureAction<String> {

    private StringBuilder signature;

    public String getSignature() {
      return signature.toString();
    }

    @Override
    public void parsedSymbol(char symbol) {
      signature.append(symbol);
    }

    @Override
    public void parsedIdentifier(String identifier) {
      signature.append(identifier);
    }

    @Override
    public String parsedTypeName(String name) {
      String type = name;
      if (originalToObfuscatedMapping != null) {
        String original = originalToObfuscatedMapping.inverse().get(name);
        type = original != null ? original : name;
      }
      signature.append(type);
      return type;
    }

    @Override
    public String parsedInnerTypeName(String enclosingType, String name) {
      String type;
      if (originalToObfuscatedMapping != null) {
        // The enclosingType has already been mapped if a mapping is present.
        String minifiedEnclosing = originalToObfuscatedMapping.get(enclosingType);
        type = originalToObfuscatedMapping.inverse().get(minifiedEnclosing + "$" + name);
        if (type != null) {
          assert type.startsWith(enclosingType + "$");
          name = type.substring(enclosingType.length() + 1);
        }
      } else {
        type = enclosingType + "$" + name;
      }
      signature.append(name);
      return type;
    }

    @Override
    public void start() {
      signature = new StringBuilder();
    }

    @Override
    public void stop() {
      // nothing to do
    }
  }
}
