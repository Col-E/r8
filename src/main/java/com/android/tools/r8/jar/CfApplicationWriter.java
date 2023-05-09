// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static com.android.tools.r8.utils.positions.LineNumberOptimizer.runAndWriteMap;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.CodeSizeOverflowDiagnostic;
import com.android.tools.r8.errors.ConstantPoolOverflowDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeAnnotation;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AsmUtils;
import com.android.tools.r8.utils.ComparatorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalGlobalSyntheticsProgramConsumer.InternalGlobalSyntheticsCfConsumer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.PredicateUtils;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassTooLargeException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class CfApplicationWriter {

  private static final boolean RUN_VERIFIER = false;
  private static final boolean PRINT_CF = false;

  // First item inserted into the constant pool is the marker string which generates an UTF8 to
  // pool index #1 and a String entry to #2, referencing #1.
  public static final int MARKER_STRING_CONSTANT_POOL_INDEX = 2;

  private final DexApplication application;
  private final AppView<?> appView;
  private final InternalOptions options;
  private final Optional<Marker> marker;
  private final Predicate<DexType> isTypeMissing;

  private static final CfVersion MIN_VERSION_FOR_COMPILER_GENERATED_CODE = CfVersion.V1_6;

  public CfApplicationWriter(AppView<?> appView, Marker marker) {
    this.application = appView.appInfo().app();
    this.appView = appView;
    this.options = appView.options();
    this.marker = Optional.ofNullable(marker);
    this.isTypeMissing =
        PredicateUtils.isNull(appView.appInfo()::definitionForWithoutExistenceAssert);
  }

  private NamingLens getNamingLens() {
    return appView.getNamingLens();
  }

  public void write(ClassFileConsumer consumer) {
    assert options.mapConsumer == null;
    write(consumer, null);
  }

  public void write(ClassFileConsumer consumer, AndroidApp inputApp) {
    application.timing.begin("CfApplicationWriter.write");
    try {
      writeApplication(inputApp, consumer);
    } finally {
      application.timing.end();
    }
  }

  private boolean includeMarker(Marker marker) {
    if (marker.isRelocator()) {
      return false;
    }
    assert marker.isCfBackend() || marker.isDexBackend();
    if (options.desugarSpecificOptions().noCfMarkerForDesugaredCode) {
      return !marker.isCfBackend() || !marker.isDesugared();
    }
    return true;
  }

  private void writeApplication(AndroidApp inputApp, ClassFileConsumer consumer) {
    ProguardMapId proguardMapId = null;
    if (options.mapConsumer != null) {
      assert marker.isPresent();
      proguardMapId =
          runAndWriteMap(
              inputApp,
              appView,
              application.timing,
              OriginalSourceFiles.fromClasses(),
              DebugRepresentation.none(options));
      marker.get().setPgMapId(proguardMapId.getId());
    }
    Optional<String> markerString = marker.filter(this::includeMarker).map(Marker::toString);
    SourceFileEnvironment sourceFileEnvironment = null;
    if (options.sourceFileProvider != null) {
      sourceFileEnvironment = ApplicationWriter.createSourceFileEnvironment(proguardMapId);
    }
    LensCodeRewriterUtils rewriter = new LensCodeRewriterUtils(appView);
    Collection<DexProgramClass> classes = application.classes();
    Collection<DexProgramClass> globalSyntheticClasses = new ArrayList<>();
    if (options.intermediate && options.hasGlobalSyntheticsConsumer()) {
      Collection<DexProgramClass> allClasses = classes;
      classes = new ArrayList<>(allClasses.size());
      for (DexProgramClass clazz : allClasses) {
        if (appView.getSyntheticItems().isGlobalSyntheticClass(clazz)) {
          globalSyntheticClasses.add(clazz);
        } else {
          classes.add(clazz);
        }
      }
    }
    for (DexProgramClass clazz : classes) {
      writeClassCatchingErrors(clazz, consumer, rewriter, markerString, sourceFileEnvironment);
    }
    if (!globalSyntheticClasses.isEmpty()) {
      InternalGlobalSyntheticsCfConsumer globalsConsumer =
          new InternalGlobalSyntheticsCfConsumer(options.getGlobalSyntheticsConsumer(), appView);
      for (DexProgramClass clazz : globalSyntheticClasses) {
        writeClassCatchingErrors(
            clazz, globalsConsumer, rewriter, markerString, sourceFileEnvironment);
      }
      globalsConsumer.finished(appView);
    }
    ApplicationWriter.supplyAdditionalConsumers(appView);
  }

  private void writeClassCatchingErrors(
      DexProgramClass clazz,
      ClassFileConsumer consumer,
      LensCodeRewriterUtils rewriter,
      Optional<String> markerString,
      SourceFileEnvironment sourceFileEnvironment) {
    assert SyntheticNaming.verifyNotInternalSynthetic(clazz.getType());
    try {
      writeClass(clazz, consumer, rewriter, markerString, sourceFileEnvironment);
    } catch (ClassTooLargeException e) {
      throw appView
          .options()
          .reporter
          .fatalError(
              new ConstantPoolOverflowDiagnostic(
                  clazz.getOrigin(),
                  Reference.classFromBinaryName(e.getClassName()),
                  e.getConstantPoolCount()));
    } catch (MethodTooLargeException e) {
      throw appView
          .options()
          .reporter
          .fatalError(
              new CodeSizeOverflowDiagnostic(
                  clazz.getOrigin(),
                  Reference.methodFromDescriptor(
                      Reference.classFromBinaryName(e.getClassName()).getDescriptor(),
                      e.getMethodName(),
                      e.getDescriptor()),
                  e.getCodeSize()));
    }
  }

  private void writeClass(
      DexProgramClass clazz,
      ClassFileConsumer consumer,
      LensCodeRewriterUtils rewriter,
      Optional<String> markerString,
      SourceFileEnvironment sourceFileEnvironment) {
    ClassWriter writer = new ClassWriter(0);
    if (markerString.isPresent()) {
      int markerStringPoolIndex = writer.newConst(markerString.get());
      assert markerStringPoolIndex == MARKER_STRING_CONSTANT_POOL_INDEX;
    }
    String sourceFile;
    if (options.sourceFileProvider == null) {
      sourceFile = clazz.sourceFile != null ? clazz.sourceFile.toString() : null;
    } else {
      sourceFile = options.sourceFileProvider.get(sourceFileEnvironment);
    }
    String sourceDebug = getSourceDebugExtension(clazz.annotations());
    writer.visitSource(sourceFile, sourceDebug);
    CfVersion version = getClassFileVersion(clazz);
    if (version.isGreaterThanOrEqualTo(CfVersion.V1_8)) {
      // JDK8 and after ignore ACC_SUPER so unset it.
      clazz.accessFlags.unsetSuper();
    } else {
      // In all other cases set the super bit as D8/R8 do not support targeting pre 1.0.2 JDKs.
      if (!clazz.accessFlags.isInterface()) {
        clazz.accessFlags.setSuper();
      }
    }
    boolean allowInvalidCfAccessFlags = false;
    if (clazz
        .getType()
        .getDescriptor()
        .endsWith(appView.dexItemFactory().createString("/package-info;"))) {
      allowInvalidCfAccessFlags = true;
    }
    int access =
        allowInvalidCfAccessFlags || options.testing.allowInvalidCfAccessFlags
            ? clazz.accessFlags.materialize()
            : clazz.accessFlags.getAsCfAccessFlags();
    if (clazz.isDeprecated()) {
      access = AsmUtils.withDeprecated(access);
    }
    String desc = getNamingLens().lookupDescriptor(clazz.type).toString();
    String name = getNamingLens().lookupInternalName(clazz.type);
    String signature = clazz.getClassSignature().toRenamedString(getNamingLens(), isTypeMissing);
    String superName =
        clazz.hasSuperType() ? getNamingLens().lookupInternalName(clazz.superType) : null;
    String[] interfaces = new String[clazz.interfaces.values.length];
    for (int i = 0; i < clazz.interfaces.values.length; i++) {
      interfaces[i] = getNamingLens().lookupInternalName(clazz.interfaces.values[i]);
    }
    assert SyntheticNaming.verifyNotInternalSynthetic(name);
    writer.visit(version.raw(), access, name, signature, superName, interfaces);
    appView.getSyntheticItems().writeAttributeIfIntermediateSyntheticClass(writer, clazz, appView);
    writeAnnotations(
        writer::visitAnnotation, writer::visitTypeAnnotation, clazz.annotations().annotations);
    ImmutableMap<DexString, DexValue> defaults = getAnnotationDefaults(clazz.annotations());

    if (clazz.getEnclosingMethodAttribute() != null) {
      clazz.getEnclosingMethodAttribute().write(writer, getNamingLens());
    }

    if (clazz.getNestHostClassAttribute() != null) {
      clazz.getNestHostClassAttribute().write(writer, getNamingLens());
    }

    for (NestMemberClassAttribute entry : clazz.getNestMembersClassAttributes()) {
      entry.write(writer, getNamingLens());
      assert clazz.getNestHostClassAttribute() == null
          : "A nest host cannot also be a nest member.";
    }

    for (PermittedSubclassAttribute entry : clazz.getPermittedSubclassAttributes()) {
      entry.write(writer, getNamingLens());
    }

    if (clazz.isRecord()) {
      // TODO(b/274888318): Strip record components if not kept.
      for (RecordComponentInfo info : clazz.getRecordComponents()) {
        info.write(writer, getNamingLens(), isTypeMissing, this::writeAnnotation);
      }
    }

    for (InnerClassAttribute entry : clazz.getInnerClasses()) {
      entry.write(writer, getNamingLens(), options);
    }

    for (DexEncodedField field : clazz.staticFields()) {
      writeField(field, writer);
    }
    for (DexEncodedField field : clazz.instanceFields()) {
      writeField(field, writer);
    }
    if (options.desugarSpecificOptions().sortMethodsOnCfOutput) {
      List<ProgramMethod> programMethodSorted = new ArrayList<>();
      clazz.forEachProgramMethod(programMethodSorted::add);
      programMethodSorted.sort(this::compareMethodsThroughLens);
      programMethodSorted.forEach(
          method -> writeMethod(method, version, rewriter, writer, defaults));
    } else {
      clazz.forEachProgramMethod(
          method -> writeMethod(method, version, rewriter, writer, defaults));
    }
    writer.visitEnd();

    byte[] result = writer.toByteArray();
    if (PRINT_CF) {
      System.out.print(printCf(result));
      System.out.flush();
    }
    if (RUN_VERIFIER) {
      // Generally, this will fail with ClassNotFoundException,
      // so don't assert that verifyCf() returns true.
      verifyCf(result);
    }
    ExceptionUtils.withConsumeResourceHandler(
        options.reporter, handler -> consumer.accept(ByteDataView.of(result), desc, handler));
  }

  private int compareTypesThroughLens(DexType a, DexType b) {
    return getNamingLens().lookupDescriptor(a).compareTo(getNamingLens().lookupDescriptor(b));
  }

  private DexString returnTypeThroughLens(DexMethod method) {
    return getNamingLens().lookupDescriptor(method.getReturnType());
  }

  private int compareMethodsThroughLens(ProgramMethod a, ProgramMethod b) {
    // When writing class files, methods are only compared within the same class.
    assert a.getHolder().equals(b.getHolder());
    return Comparator.comparing(this::returnTypeThroughLens)
        .thenComparing(DexMethod::getName)
        // .thenComparingInt(m -> m.getProto().getArity()) // Done in arrayComp below.
        .thenComparing(
            m -> m.getProto().parameters.values,
            ComparatorUtils.arrayComparator(this::compareTypesThroughLens))
        .compare(a.getReference(), b.getReference());
  }

  private CfVersion getClassFileVersion(DexEncodedMethod method) {
    if (!method.hasClassFileVersion()) {
      // In this case bridges have been introduced for the Cf back-end,
      // which do not have class file version.
      assert options.isDesugaredLibraryCompilation() || options.isDesugaring()
          : "Expected class file version for " + method.getReference().toSourceString();
      assert MIN_VERSION_FOR_COMPILER_GENERATED_CODE.isLessThan(
          options.classFileVersionAfterDesugaring(InternalOptions.SUPPORTED_CF_VERSION));
      // Any desugaring rewrites which cannot meet the default class file version after
      // desugaring must upgrade the class file version during desugaring.
      return options.isDesugaring()
          ? options.classFileVersionAfterDesugaring(InternalOptions.SUPPORTED_CF_VERSION)
          : MIN_VERSION_FOR_COMPILER_GENERATED_CODE;
    }
    return method.getClassFileVersion();
  }

  private CfVersion getClassFileVersion(DexProgramClass clazz) {
    CfVersion version =
        clazz.hasClassFileVersion()
            ? clazz.getInitialClassFileVersion()
            : MIN_VERSION_FOR_COMPILER_GENERATED_CODE;
    for (DexEncodedMethod method : clazz.directMethods()) {
      version = Ordered.max(version, getClassFileVersion(method));
    }
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      version = Ordered.max(version, getClassFileVersion(method));
    }
    return version;
  }

  private DexValue getSystemAnnotationValue(DexAnnotationSet annotations, DexType type) {
    DexAnnotation annotation = annotations.getFirstMatching(type);
    if (annotation == null) {
      return null;
    }
    assert annotation.visibility == DexAnnotation.VISIBILITY_SYSTEM;
    DexEncodedAnnotation encodedAnnotation = annotation.annotation;
    assert encodedAnnotation.elements.length == 1;
    return encodedAnnotation.elements[0].value;
  }

  private String getSourceDebugExtension(DexAnnotationSet annotations) {
    DexValue debugExtensions =
        getSystemAnnotationValue(
            annotations, application.dexItemFactory.annotationSourceDebugExtension);
    if (debugExtensions == null) {
      return null;
    }
    return debugExtensions.asDexValueString().getValue().toString();
  }

  private ImmutableMap<DexString, DexValue> getAnnotationDefaults(DexAnnotationSet annotations) {
    DexValue value =
        getSystemAnnotationValue(annotations, application.dexItemFactory.annotationDefault);
    if (value == null) {
      return ImmutableMap.of();
    }
    DexEncodedAnnotation annotation = value.asDexValueAnnotation().value;
    Builder<DexString, DexValue> builder = ImmutableMap.builder();
    for (DexAnnotationElement element : annotation.elements) {
      builder.put(element.name, element.value);
    }
    return builder.build();
  }

  private String[] getExceptions(DexAnnotationSet annotations) {
    DexValue value =
        getSystemAnnotationValue(annotations, application.dexItemFactory.annotationThrows);
    if (value == null) {
      return null;
    }
    DexValue[] values = value.asDexValueArray().getValues();
    String[] res = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      res[i] = getNamingLens().lookupInternalName(values[i].asDexValueType().value);
    }
    return res;
  }

  private Object getStaticValue(DexEncodedField field) {
    if (!field.accessFlags.isStatic() || !field.hasExplicitStaticValue()) {
      return null;
    }
    return field.getStaticValue().asAsmEncodedObject();
  }

  private void writeField(DexEncodedField field, ClassWriter writer) {
    int access = field.accessFlags.getAsCfAccessFlags();
    if (field.isDeprecated()) {
      access = AsmUtils.withDeprecated(access);
    }
    String name = getNamingLens().lookupName(field.getReference()).toString();
    String desc = getNamingLens().lookupDescriptor(field.getReference().type).toString();
    String signature = field.getGenericSignature().toRenamedString(getNamingLens(), isTypeMissing);
    Object value = getStaticValue(field);
    FieldVisitor visitor = writer.visitField(access, name, desc, signature, value);
    writeAnnotations(
        visitor::visitAnnotation, visitor::visitTypeAnnotation, field.annotations().annotations);
    visitor.visitEnd();
  }

  private void writeMethod(
      ProgramMethod method,
      CfVersion classFileVersion,
      LensCodeRewriterUtils rewriter,
      ClassWriter writer,
      ImmutableMap<DexString, DexValue> defaults) {
    // For "pass through" classes which has already been library desugared use the identity lens.
    NamingLens namingLens =
        appView.isAlreadyLibraryDesugared(method.getHolder())
            ? NamingLens.getIdentityLens()
            : getNamingLens();
    DexEncodedMethod definition = method.getDefinition();
    int access = definition.getAccessFlags().getAsCfAccessFlags();
    if (definition.isDeprecated()) {
      access = AsmUtils.withDeprecated(access);
    }
    String name = namingLens.lookupName(method.getReference()).toString();
    String desc = definition.descriptor(namingLens);
    String signature =
        method.getDefinition().getGenericSignature().toRenamedString(namingLens, isTypeMissing);
    String[] exceptions = getExceptions(definition.annotations());
    MethodVisitor visitor = writer.visitMethod(access, name, desc, signature, exceptions);
    if (defaults.containsKey(definition.getName())) {
      AnnotationVisitor defaultVisitor = visitor.visitAnnotationDefault();
      if (defaultVisitor != null) {
        writeAnnotationElement(defaultVisitor, null, defaults.get(definition.getName()));
        defaultVisitor.visitEnd();
      }
    }
    writeMethodParametersAnnotation(visitor, definition.annotations().annotations);
    writeAnnotations(
        visitor::visitAnnotation,
        visitor::visitTypeAnnotation,
        definition.annotations().annotations);
    writeParameterAnnotations(visitor, definition.parameterAnnotationsList);
    if (!definition.shouldNotHaveCode()) {
      writeCode(method, classFileVersion, namingLens, rewriter, visitor);
    }
    visitor.visitEnd();
  }

  private void writeMethodParametersAnnotation(MethodVisitor visitor, DexAnnotation[] annotations) {
    for (DexAnnotation annotation : annotations) {
      if (annotation.annotation.type == appView.dexItemFactory().annotationMethodParameters) {
        assert annotation.visibility == DexAnnotation.VISIBILITY_SYSTEM;
        assert annotation.annotation.elements.length == 2;
        assert annotation.annotation.elements[0].name.toString().equals("names");
        assert annotation.annotation.elements[1].name.toString().equals("accessFlags");
        DexValueArray names = annotation.annotation.elements[0].value.asDexValueArray();
        DexValueArray accessFlags = annotation.annotation.elements[1].value.asDexValueArray();
        assert names != null && accessFlags != null;
        assert names.getValues().length == accessFlags.getValues().length;
        for (int i = 0; i < names.getValues().length; i++) {
          DexValueString name = names.getValues()[i].asDexValueString();
          DexValueInt access = accessFlags.getValues()[i].asDexValueInt();
          String nameString = name != null ? name.value.toString() : null;
          visitor.visitParameter(nameString, access.value);
        }
      }
    }
  }

  private void writeParameterAnnotations(
      MethodVisitor visitor, ParameterAnnotationsList parameterAnnotations) {
    // TODO(113565942): We currently assume that the annotable parameter count
    // it the same for visible and invisible annotations. That doesn't actually
    // seem to be the case in the class file format.
    visitor.visitAnnotableParameterCount(
        parameterAnnotations.getAnnotableParameterCount(), true);
    visitor.visitAnnotableParameterCount(
        parameterAnnotations.getAnnotableParameterCount(), false);
    for (int i = 0; i < parameterAnnotations.size(); i++) {
      int iFinal = i;
      writeAnnotations(
          (d, vis) -> visitor.visitParameterAnnotation(iFinal, d, vis),
          (typeRef, typePath, desc, visible) -> {
            throw new Unreachable("Type annotations are not placed on parameters");
          },
          parameterAnnotations.get(i).annotations);
    }
  }

  private interface AnnotationConsumer {
    AnnotationVisitor visit(String desc, boolean visible);
  }

  private interface TypeAnnotationConsumer {
    AnnotationVisitor visit(int typeRef, TypePath typePath, String desc, boolean visible);
  }

  private void writeAnnotations(
      AnnotationConsumer visitor,
      TypeAnnotationConsumer typeAnnotationVisitor,
      DexAnnotation[] annotations) {
    for (DexAnnotation dexAnnotation : annotations) {
      if (dexAnnotation.visibility == DexAnnotation.VISIBILITY_SYSTEM) {
        // Annotations with VISIBILITY_SYSTEM are not annotations in CF, but are special
        // annotations in Dex, i.e. default, enclosing class, enclosing method, member classes,
        // signature, throws.
        continue;
      }
      String desc = getNamingLens().lookupDescriptor(dexAnnotation.annotation.type).toString();
      boolean visible = dexAnnotation.visibility == DexAnnotation.VISIBILITY_RUNTIME;
      DexTypeAnnotation dexTypeAnnotation = dexAnnotation.asTypeAnnotation();
      AnnotationVisitor v =
          dexTypeAnnotation == null
              ? visitor.visit(desc, visible)
              : typeAnnotationVisitor.visit(
                  dexTypeAnnotation.getTypeRef(), dexTypeAnnotation.getTypePath(), desc, visible);
      if (v != null) {
        writeAnnotation(v, dexAnnotation.annotation);
        v.visitEnd();
      }
    }
  }

  private void writeAnnotation(AnnotationVisitor v, DexEncodedAnnotation annotation) {
    for (DexAnnotationElement element : annotation.elements) {
      writeAnnotationElement(v, element.name.toString(), element.value);
    }
  }

  private void writeAnnotationElement(AnnotationVisitor visitor, String name, DexValue value) {
    switch (value.getValueKind()) {
      case ANNOTATION:
        {
          DexValueAnnotation valueAnnotation = value.asDexValueAnnotation();
          AnnotationVisitor innerVisitor =
              visitor.visitAnnotation(
                  name, getNamingLens().lookupDescriptor(valueAnnotation.value.type).toString());
          if (innerVisitor != null) {
            writeAnnotation(innerVisitor, valueAnnotation.value);
            innerVisitor.visitEnd();
          }
        }
        break;

      case ARRAY:
        {
          DexValue[] values = value.asDexValueArray().getValues();
          AnnotationVisitor innerVisitor = visitor.visitArray(name);
          if (innerVisitor != null) {
            for (DexValue elementValue : values) {
              writeAnnotationElement(innerVisitor, null, elementValue);
            }
            innerVisitor.visitEnd();
          }
        }
        break;

      case ENUM:
        DexField enumField = value.asDexValueEnum().getValue();
        // This must not be renamed, as the Java runtime will use Enum.valueOf to find the enum's
        // referenced in annotations. See b/236691999 for details.
        assert getNamingLens().lookupName(enumField) == enumField.name;
        visitor.visitEnum(
            name,
            getNamingLens().lookupDescriptor(enumField.getType()).toString(),
            enumField.name.toString());
        break;

      case FIELD:
        throw new Unreachable("writeAnnotationElement of DexValueField");

      case METHOD:
        throw new Unreachable("writeAnnotationElement of DexValueMethod");

      case METHOD_HANDLE:
        throw new Unreachable("writeAnnotationElement of DexValueMethodHandle");

      case METHOD_TYPE:
        throw new Unreachable("writeAnnotationElement of DexValueMethodType");

      case STRING:
        visitor.visit(name, value.asDexValueString().getValue().toString());
        break;

      case TYPE:
        visitor.visit(
            name,
            Type.getType(
                getNamingLens().lookupDescriptor(value.asDexValueType().value).toString()));
        break;

      default:
        visitor.visit(name, value.getBoxedValue());
        break;
    }
  }

  private void writeCode(
      ProgramMethod method,
      CfVersion classFileVersion,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    Code code = method.getDefinition().getCode();
    assert code.isCfWritableCode();
    assert code.estimatedDexCodeSizeUpperBoundInBytes() > 0;
    if (!code.isCfWritableCode()) {
      // This should never happen (see assertion above), but desugaring bugs may lead the
      // CfApplicationWriter to try to write invalid code and we need the better error message.
      throw new Unreachable(
          "The CfApplicationWriter cannot write non cf writable code "
              + code.getClass().getCanonicalName()
              + " for method "
              + method.getReference().toSourceString());
    }
    code.asCfWritableCode()
        .writeCf(method, classFileVersion, appView, namingLens, rewriter, visitor);
  }

  public static String printCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    ClassNode node = new ClassNode(ASM_VERSION);
    reader.accept(node, ASM_VERSION);
    StringWriter writer = new StringWriter();
    for (MethodNode method : node.methods) {
      writer.append(method.name).append(method.desc).append('\n');
      TraceMethodVisitor visitor = new TraceMethodVisitor(new Textifier());
      method.accept(visitor);
      visitor.p.print(new PrintWriter(writer));
      writer.append('\n');
    }
    return writer.toString();
  }

  private static void verifyCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    PrintWriter pw = new PrintWriter(System.out);
    CheckClassAdapter.verify(reader, false, pw);
  }
}
