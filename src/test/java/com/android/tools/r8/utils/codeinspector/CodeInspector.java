// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.SwitchPayloadResolver;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.signature.GenericSignatureAction;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.MappingSupplierInternalImpl;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BiMapContainer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CodeInspector {

  final DexApplication application;
  final DexItemFactory dexItemFactory;
  private final ClassNameMapper mapping;
  final Map<String, String> originalToObfuscatedMapping;
  final Map<String, String> obfuscatedToOriginalMapping;
  private Retracer lazyRetracer = null;

  public static MethodSignature MAIN =
      new MethodSignature("main", "void", new String[] {"java.lang.String[]"});

  public CodeInspector(String path) throws IOException {
    this(Paths.get(path));
  }

  public CodeInspector(Path file, String proguardMapContent) throws IOException {
    this(AndroidApp.builder().addProgramFile(file).build(), proguardMapContent);
  }

  public CodeInspector(Path file, Path mappingFile) throws IOException {
    this(
        Collections.singletonList(file), mappingFile != null ? mappingFile.toString() : null, null);
  }

  public CodeInspector(Path file) throws IOException {
    this(Collections.singletonList(file), null, null);
  }

  public CodeInspector(Path file, Consumer<InternalOptions> optionsConsumer) throws IOException {
    this(Collections.singletonList(file), null, optionsConsumer);
  }

  public CodeInspector(Collection<Path> files) throws IOException {
    this(files, null, null);
  }

  public CodeInspector(
      Collection<Path> files, String mappingFile, Consumer<InternalOptions> optionsConsumer)
      throws IOException {
    Path mappingPath = mappingFile != null ? Paths.get(mappingFile) : null;
    if (mappingPath != null && Files.exists(mappingPath)) {
      mapping = ClassNameMapper.mapperFromFile(mappingPath);
      BiMapContainer<String, String> nameMapping = mapping.getObfuscatedToOriginalMapping();
      obfuscatedToOriginalMapping = nameMapping.original;
      originalToObfuscatedMapping = nameMapping.inverse;
    } else {
      mapping = null;
      originalToObfuscatedMapping = null;
      obfuscatedToOriginalMapping = null;
    }
    Timing timing = Timing.empty();
    InternalOptions options = runOptionsConsumer(optionsConsumer);
    dexItemFactory = options.itemFactory;
    AndroidApp input = AndroidApp.builder().addProgramFiles(files).build();
    application = new ApplicationReader(input, options, timing).read();
  }

  public CodeInspector(AndroidApp app) throws IOException {
    this(app, (Consumer<InternalOptions>) null);
  }

  public CodeInspector(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException {
    this(
        new ApplicationReader(app, runOptionsConsumer(optionsConsumer), Timing.empty())
            .read(app.getProguardMapOutputData()));
  }

  public static CodeInspector empty() throws IOException {
    return new CodeInspector(ImmutableList.of(), null, null);
  }

  private static InternalOptions runOptionsConsumer(Consumer<InternalOptions> optionsConsumer) {
    InternalOptions internalOptions = new InternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(internalOptions);
    }
    if (internalOptions.programConsumer == null) {
      // The inspector allows building IR for a method. An output type must be defined for that.
      internalOptions.programConsumer = DexIndexedConsumer.emptyConsumer();
    }
    // Always allow use of experimental map-file reading in the inspector.
    internalOptions.testing.enableExperimentalMapFileVersion = true;
    return internalOptions;
  }

  public CodeInspector(AndroidApp app, Path proguardMapFile) throws IOException {
    this(
        new ApplicationReader(app, runOptionsConsumer(null), Timing.empty())
            .read(StringResource.fromFile(proguardMapFile)));
  }

  public CodeInspector(AndroidApp app, String proguardMapContent) throws IOException {
    this(
        new ApplicationReader(app, runOptionsConsumer(null), Timing.empty())
            .read(
                StringResource.fromString(proguardMapContent, Origin.unknown())));
  }

  public CodeInspector(DexApplication application) {
    dexItemFactory = application.dexItemFactory;
    this.application = application;
    this.mapping = application.getProguardMap();
    if (mapping == null) {
      originalToObfuscatedMapping = null;
      obfuscatedToOriginalMapping = null;
    } else {
      BiMapContainer<String, String> nameMapping = mapping.getObfuscatedToOriginalMapping();
      obfuscatedToOriginalMapping = nameMapping.original;
      originalToObfuscatedMapping = nameMapping.inverse;
    }
  }

  public DexApplication getApplication() {
    return application;
  }

  public DexItemFactory getFactory() {
    return dexItemFactory;
  }

  public Retracer getRetracer() {
    if (lazyRetracer == null) {
      lazyRetracer =
          RetracerImpl.createInternal(
              MappingSupplierInternalImpl.createInternal(mapping),
              new TestDiagnosticMessagesImpl());
    }
    return lazyRetracer;
  }

  DexType toDexType(String string) {
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(string));
  }

  private DexType toDexTypeIgnorePrimitives(String string) {
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptorIgnorePrimitives(string));
  }

  public TypeSubject getTypeSubject(String string) {
    return new TypeSubject(
        this, dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(string)));
  }

  String mapType(Map<String, String> mapping, String typeName) {
    final String ARRAY_POSTFIX = "[]";
    int arrayCount = 0;
    while (typeName.endsWith(ARRAY_POSTFIX)) {
      arrayCount++;
      typeName = typeName.substring(0, typeName.length() - 2);
    }
    String mappedType = mapping.get(typeName);
    if (mappedType == null) {
      return null;
    }
    for (int i = 0; i < arrayCount; i++) {
      mappedType += ARRAY_POSTFIX;
    }
    return mappedType;
  }

  static <S, T extends Subject> void forAll(
      Iterable<? extends S> items,
      BiFunction<S, FoundClassSubject, ? extends T> constructor,
      FoundClassSubject clazz,
      Consumer<T> consumer) {
    for (S item : items) {
      consumer.accept(constructor.apply(item, clazz));
    }
  }

  private static <S, T extends Subject> void forAll(
      Iterable<S> items, Function<S, T> constructor, Consumer<T> consumer) {
    for (S item : items) {
      consumer.accept(constructor.apply(item));
    }
  }

  public DexAnnotation findAnnotation(DexAnnotationSet annotationSet, String name) {
    return findAnnotation(
        annotationSet,
        annotation -> {
          DexType type = annotation.annotation.type;
          String original = mapping == null ? type.toSourceString() : mapping.originalNameOf(type);
          return original.equals(name);
        });
  }

  public DexAnnotation findAnnotation(
      DexAnnotationSet annotationSet, Predicate<DexAnnotation> predicate) {
    List<DexAnnotation> annotations = findAnnotations(annotationSet, predicate);
    assert annotations.size() <= 1;
    return annotations.isEmpty() ? null : annotations.get(0);
  }

  public List<DexAnnotation> findAnnotations(
      DexAnnotationSet annotationSet, Predicate<DexAnnotation> predicate) {
    return Arrays.stream(annotationSet.annotations).filter(predicate).collect(Collectors.toList());
  }

  public String getOriginalSignatureAttribute(
      String finalSignature, BiConsumer<GenericSignatureParser, String> parse) {
    if (finalSignature == null || mapping == null) {
      return finalSignature;
    }
    GenericSignatureGenerator rewriter = new GenericSignatureGenerator();
    GenericSignatureParser<String> parser = new GenericSignatureParser<>(rewriter);
    parse.accept(parser, finalSignature);
    return rewriter.getSignature();
  }

  public ClassSubject clazz(Class<?> clazz) {
    return clazz(Reference.classFromClass(clazz));
  }

  /** Lookup a class by name. This allows both original and obfuscated names. */
  public ClassSubject clazz(String name) {
    return clazz(Reference.classFromTypeName(name));
  }

  public ClassNameMapper getMapping() {
    return mapping;
  }

  // Simple wrapper to more easily change the implementation for retracing subjects.
  // This should in time be replaced by use of the Retrace API.
  public static class MappingWrapper {

    private static final MappingWrapper EMPTY =
        new MappingWrapper(null, null) {
          @Override
          public Collection<MappingInformation> getAdditionalMappings() {
            return Collections.emptyList();
          }

          @Override
          public ClassNamingForNameMapper getNaming() {
            return null;
          }
        };

    static MappingWrapper create(ClassNameMapper mapper, ClassNamingForNameMapper naming) {
      if (mapper == null || naming == null) {
        return EMPTY;
      }
      return new MappingWrapper(mapper, naming);
    }

    private final ClassNameMapper mapper;
    private final ClassNamingForNameMapper naming;

    private MappingWrapper(ClassNameMapper mapper, ClassNamingForNameMapper naming) {
      this.mapper = mapper;
      this.naming = naming;
    }

    public Collection<MappingInformation> getAdditionalMappings() {
      return naming.getAdditionalMappingInfo();
    }

    public ClassNamingForNameMapper getNaming() {
      assert naming != null;
      return naming;
    }
  }

  public ClassSubject clazz(ClassReference reference) {
    String descriptor = reference.getDescriptor();
    String name = DescriptorUtils.descriptorToJavaType(descriptor);
    ClassNamingForNameMapper naming = null;
    if (mapping != null) {
      String obfuscated = originalToObfuscatedMapping.get(name);
      if (obfuscated != null) {
        naming = mapping.getClassNaming(obfuscated);
        name = obfuscated;
      } else {
        // Figure out if the name is an already obfuscated name.
        String original = obfuscatedToOriginalMapping.get(name);
        if (original != null) {
          naming = mapping.getClassNaming(name);
        }
      }
    }
    DexClass clazz = application.definitionFor(toDexTypeIgnorePrimitives(name));
    if (clazz == null) {
      return new AbsentClassSubject(this, reference);
    }
    return new FoundClassSubject(this, clazz, MappingWrapper.create(mapping, naming), reference);
  }

  public ClassSubject companionClassFor(Class<?> clazz) {
    return clazz(SyntheticItemsTestUtils.syntheticCompanionClass(clazz));
  }

  public void forAllClasses(Consumer<FoundClassSubject> inspection) {
    forAll(
        application.classes(),
        cls -> {
          ClassSubject subject = clazz(cls.type.toSourceString());
          assert subject.isPresent();
          return (FoundClassSubject) subject;
        },
        inspection);
  }

  public List<FoundClassSubject> allClasses() {
    ImmutableList.Builder<FoundClassSubject> builder = ImmutableList.builder();
    forAllClasses(builder::add);
    return builder.build();
  }

  public FieldSubject field(Field field) {
    return field(Reference.fieldFromField(field));
  }

  public FieldSubject field(FieldReference field) {
    ClassSubject clazz = clazz(field.getHolderClass());
    if (!clazz.isPresent()) {
      return new AbsentFieldSubject();
    }
    return clazz.field(field.getFieldType().getTypeName(), field.getFieldName());
  }

  public MethodSubject method(Method method) {
    return method(Reference.methodFromMethod(method));
  }

  public MethodSubject method(MethodReference method) {
    ClassSubject clazz = clazz(method.getHolderClass());
    if (!clazz.isPresent()) {
      return new AbsentMethodSubject();
    }
    return clazz.method(method);
  }

  String getObfuscatedTypeName(String originalTypeName) {
    String obfuscatedTypeName = null;
    if (mapping != null) {
      obfuscatedTypeName = mapType(originalToObfuscatedMapping, originalTypeName);
    }
    return obfuscatedTypeName != null ? obfuscatedTypeName : originalTypeName;
  }

  String getOriginalTypeName(String minifiedTypeName) {
    String originalTypeName = null;
    if (mapping != null) {
      originalTypeName = mapType(obfuscatedToOriginalMapping, minifiedTypeName);
    }
    return originalTypeName != null ? originalTypeName : minifiedTypeName;
  }

  InstructionSubject createInstructionSubject(
      DexInstruction instruction,
      MethodSubject method,
      SwitchPayloadResolver switchPayloadResolver) {
    DexInstructionSubject dexInst = new DexInstructionSubject(instruction, method);
    if (dexInst.isInvoke()) {
      return new InvokeDexInstructionSubject(this, instruction, method);
    } else if (dexInst.isFieldAccess()) {
      return new FieldAccessDexInstructionSubject(this, instruction, method);
    } else if (dexInst.isNewInstance()) {
      return new NewInstanceDexInstructionSubject(instruction, method);
    } else if (dexInst.isConstString(JumboStringMode.ALLOW)) {
      return new ConstStringDexInstructionSubject(instruction, method);
    } else if (dexInst.isCheckCast()) {
      return new CheckCastDexInstructionSubject(instruction, method);
    } else if (dexInst.isSwitch()) {
      return new SwitchDexInstructionSubject(instruction, method, switchPayloadResolver);
    } else {
      return dexInst;
    }
  }

  InstructionSubject createInstructionSubject(CfInstruction instruction, MethodSubject method) {
    CfInstructionSubject cfInst = new CfInstructionSubject(instruction, method);
    if (cfInst.isInvoke()) {
      return new InvokeCfInstructionSubject(this, instruction, method);
    } else if (cfInst.isFieldAccess()) {
      return new FieldAccessCfInstructionSubject(this, instruction, method);
    } else if (cfInst.isNewInstance()) {
      return new NewInstanceCfInstructionSubject(instruction, method);
    } else if (cfInst.isConstString(JumboStringMode.ALLOW)) {
      return new ConstStringCfInstructionSubject(instruction, method);
    } else if (cfInst.isCheckCast()) {
      return new CheckCastCfInstructionSubject(instruction, method);
    } else if (cfInst.isSwitch()) {
      return new SwitchCfInstructionSubject(instruction, method);
    } else {
      return cfInst;
    }
  }

  InstructionIterator createInstructionIterator(MethodSubject method) {
    Code code = method.getMethod().getCode();
    assert code != null;
    if (code.isDexCode()) {
      return new DexInstructionIterator(this, method);
    } else if (code.isCfCode()) {
      return new CfInstructionIterator(this, method);
    } else {
      throw new Unimplemented("InstructionIterator is implemented for DexCode and CfCode only.");
    }
  }

  TryCatchSubject createTryCatchSubject(DexCode code, Try tryElement, TryHandler tryHandler) {
    return new DexTryCatchSubject(this, code, tryElement, tryHandler);
  }

  TryCatchSubject createTryCatchSubject(CfCode code, CfTryCatch tryCatch) {
    return new CfTryCatchSubject(this, code, tryCatch);
  }

  TryCatchIterator createTryCatchIterator(MethodSubject method) {
    Code code = method.getMethod().getCode();
    assert code != null;
    if (code.isDexCode()) {
      return new DexTryCatchIterator(this, method);
    } else if (code.isCfCode()) {
      return new CfTryCatchIterator(this, method);
    } else {
      throw new Unimplemented("TryCatchIterator is implemented for DexCode and CfCode only.");
    }
  }

  public Collection<Marker> getMarkers() {
    return dexItemFactory.extractMarkers();
  }

  // Build the generic signature using the current mapping if any.
  class GenericSignatureGenerator implements GenericSignatureAction<String> {

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
    public String parsedTypeName(String name, ParserPosition parserPosition) {
      String type = name;
      if (obfuscatedToOriginalMapping != null) {
        String original = mapType(obfuscatedToOriginalMapping, name);
        type = original != null ? original : name;
      }
      signature.append(type);
      return type;
    }

    @Override
    public String parsedInnerTypeName(String enclosingType, String name) {
      String type = null;
      if (originalToObfuscatedMapping != null) {
        // The enclosingType has already been mapped if a mapping is present.
        String minifiedEnclosing = originalToObfuscatedMapping.get(enclosingType);
        if (minifiedEnclosing != null) {
          assert !minifiedEnclosing.contains("[");
          type = mapType(obfuscatedToOriginalMapping, minifiedEnclosing + "$" + name);
          if (type != null) {
            assert type.startsWith(enclosingType + "$");
            name = type.substring(enclosingType.length() + 1);
          }
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

  public Retracer retrace() {
    return RetracerImpl.createInternal(
        MappingSupplierInternalImpl.createInternal(
            mapping == null ? ClassNameMapper.builder().build() : mapping),
        new TestDiagnosticMessagesImpl());
  }
}
