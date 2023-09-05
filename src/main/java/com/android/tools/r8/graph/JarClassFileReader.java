// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_DEPRECATED;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueBoolean;
import com.android.tools.r8.graph.DexValue.DexValueByte;
import com.android.tools.r8.graph.DexValue.DexValueChar;
import com.android.tools.r8.graph.DexValue.DexValueDouble;
import com.android.tools.r8.graph.DexValue.DexValueEnum;
import com.android.tools.r8.graph.DexValue.DexValueFloat;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueLong;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.DexValue.DexValueShort;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticMarker;
import com.android.tools.r8.utils.AsmUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/** Java/Jar class reader for constructing dex/graph structure. */
public class JarClassFileReader<T extends DexClass> {

  private static final byte[] CLASSFILE_HEADER = ByteBuffer.allocate(4).putInt(0xCAFEBABE).array();

  // Hidden ASM "synthetic attribute" bit we need to clear.
  private static final int ACC_SYNTHETIC_ATTRIBUTE = 0x40000;

  private final JarApplicationReader application;
  private final Consumer<T> classConsumer;
  private final ClassKind<T> classKind;

  public JarClassFileReader(
      JarApplicationReader application, Consumer<T> classConsumer, ClassKind<T> classKind) {
    this.application = application;
    this.classConsumer = classConsumer;
    this.classKind = classKind;
  }

  public void read(ProgramResource resource) throws ResourceException {
    read(resource.getOrigin(), resource.getBytes());
  }

  public void read(Origin origin, byte[] bytes) {
    ExceptionUtils.withOriginAttachmentHandler(origin, () -> internalRead(origin, bytes));
  }

  public void internalRead(Origin origin, byte[] bytes) {
    if (bytes.length < CLASSFILE_HEADER.length) {
      throw new CompilationError("Invalid empty classfile", origin);
    }
    for (int i = 0; i < CLASSFILE_HEADER.length; i++) {
      if (bytes[i] != CLASSFILE_HEADER[i]) {
        throw new CompilationError("Invalid classfile header", origin);
      }
    }

    if (classKind == ClassKind.PROGRAM
        && application.options.isDesugaring()
        && application.options.desugarGraphConsumer != null) {
      application.options.desugarGraphConsumer.acceptProgramNode(origin);
    }

    ClassReader reader = new ClassReader(bytes);

    int parsingOptions = SKIP_FRAMES | SKIP_CODE;
    if (classKind != ClassKind.PROGRAM) {
      parsingOptions |= SKIP_DEBUG;
    }
    reader.accept(
        new CreateDexClassVisitor<>(origin, classKind, reader.b, application, classConsumer),
        getAttributePrototypes(),
        parsingOptions);

    // Read marker.
    if (reader.getItemCount() > CfApplicationWriter.MARKER_STRING_CONSTANT_POOL_INDEX
        && reader.getItem(CfApplicationWriter.MARKER_STRING_CONSTANT_POOL_INDEX) > 0) {
      try {
        Object maybeMarker =
            reader.readConst(
                CfApplicationWriter.MARKER_STRING_CONSTANT_POOL_INDEX,
                new char[reader.getMaxStringLength()]);
        if (maybeMarker instanceof String) {
          application.getFactory().createMarkerString((String) maybeMarker);
        }
      } catch (IllegalArgumentException e) {
        // Ignore if the type of the constant is not something readConst() allows.
      }
    }
  }

  private Attribute[] getAttributePrototypes() {
    if (classKind == ClassKind.PROGRAM) {
      return new Attribute[] {
        SyntheticMarker.getMarkerAttributePrototype(application.getFactory().getSyntheticNaming())
      };
    }
    return new Attribute[0];
  }

  private static int cleanAccessFlags(int access) {
    // Clear the "synthetic attribute" and "deprecated" attribute-flags if present.
    return access & ~ACC_SYNTHETIC_ATTRIBUTE & ~ACC_DEPRECATED;
  }

  public static FieldAccessFlags createFieldAccessFlags(int access) {
    return FieldAccessFlags.fromCfAccessFlags(cleanAccessFlags(access));
  }

  public static MethodAccessFlags createMethodAccessFlags(String name, int access) {
    boolean isConstructor =
        name.equals(Constants.INSTANCE_INITIALIZER_NAME)
            || name.equals(Constants.CLASS_INITIALIZER_NAME);
    return MethodAccessFlags.fromCfAccessFlags(cleanAccessFlags(access), isConstructor);
  }

  private static AnnotationVisitor createAnnotationVisitor(
      String desc,
      boolean visible,
      List<DexAnnotation> annotations,
      JarApplicationReader application,
      BiFunction<Integer, DexEncodedAnnotation, DexAnnotation> newAnnotationConstructor) {
    assert newAnnotationConstructor != null;
    if (visible || retainCompileTimeAnnotation(desc, application)) {
      int visibility = visible ? DexAnnotation.VISIBILITY_RUNTIME : DexAnnotation.VISIBILITY_BUILD;
      return new CreateAnnotationVisitor(
          application,
          (names, values) ->
              annotations.add(
                  newAnnotationConstructor.apply(
                      visibility, createEncodedAnnotation(desc, names, values, application))));
    }
    return null;
  }

  private static AnnotationVisitor createTypeAnnotationVisitor(
      String desc,
      boolean visible,
      List<DexAnnotation> annotations,
      JarApplicationReader application,
      int typeRef,
      TypePath typePath) {
    assert annotations != null;
    // Java 8 type annotations are not supported by DEX. Ignore them.
    if (!application.options.isGeneratingClassFiles()) {
      return null;
    }
    return createAnnotationVisitor(
        desc,
        visible,
        annotations,
        application,
        (visibility, annotation) ->
            new DexTypeAnnotation(visibility, annotation, typeRef, typePath));
  }

  private static boolean retainCompileTimeAnnotation(
      String desc, JarApplicationReader application) {
    return DexAnnotation.retainCompileTimeAnnotation(
        application.getTypeFromDescriptor(desc), application.options);
  }

  private static DexEncodedAnnotation createEncodedAnnotation(String desc,
      List<DexString> names, List<DexValue> values, JarApplicationReader application) {
    assert (names == null && values.isEmpty())
        || (names != null && !names.isEmpty() && names.size() == values.size());
    DexAnnotationElement[] elements = new DexAnnotationElement[values.size()];
    for (int i = 0; i < values.size(); i++) {
      elements[i] = new DexAnnotationElement(names.get(i), values.get(i));
    }
    return new DexEncodedAnnotation(application.getTypeFromDescriptor(desc), elements);
  }

  private static class CreateDexClassVisitor<T extends DexClass> extends ClassVisitor {

    private final Origin origin;
    private final ClassKind<T> classKind;
    private final JarApplicationReader application;
    private final Consumer<T> classConsumer;
    private final ReparseContext context = new ReparseContext();

    // DexClass data.
    private CfVersion version;
    private boolean deprecated;
    private DexType type;
    private ClassAccessFlags accessFlags;
    private DexType superType;
    private DexTypeList interfaces;
    private DexString sourceFile;
    private NestHostClassAttribute nestHost = null;
    private final List<NestMemberClassAttribute> nestMembers = new ArrayList<>();
    private final List<PermittedSubclassAttribute> permittedSubclasses = new ArrayList<>();
    private final List<RecordComponentInfo> recordComponents = new ArrayList<>();
    private EnclosingMethodAttribute enclosingMember = null;
    private final List<InnerClassAttribute> innerClasses = new ArrayList<>();
    private ClassSignature classSignature = ClassSignature.noSignature();
    private List<DexAnnotation> annotations = null;
    private List<DexAnnotationElement> defaultAnnotations = null;
    private final List<DexEncodedField> staticFields = new ArrayList<>();
    private final List<DexEncodedField> instanceFields = new ArrayList<>();
    private final Set<Wrapper<DexField>> fieldSignatures = new HashSet<>();
    private final List<DexEncodedMethod> directMethods = new ArrayList<>();
    private final List<DexEncodedMethod> virtualMethods = new ArrayList<>();
    private final Set<Wrapper<DexMethod>> methodSignatures = new HashSet<>();
    private boolean hasReachabilitySensitiveMethod = false;
    private SyntheticMarker syntheticMarker = null;

    public CreateDexClassVisitor(
        Origin origin,
        ClassKind<T> classKind,
        byte[] classCache,
        JarApplicationReader application,
        Consumer<T> classConsumer) {
      super(ASM_VERSION);
      this.origin = origin;
      this.classKind = classKind;
      this.classConsumer = classConsumer;
      this.context.classCache = classCache;
      this.application = application;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
      SyntheticMarker marker = SyntheticMarker.readMarkerAttribute(attribute);
      if (marker != null) {
        assert syntheticMarker == null;
        syntheticMarker = marker;
      }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
      if (outerName != null && innerName != null) {
        String separator = DescriptorUtils.computeInnerClassSeparator(outerName, name, innerName);
        if (separator == null && version.isLessThan(CfVersion.V9)) {
          application.options.reporter.info(
              new StringDiagnostic(
                  StringUtils.lines(
                      "Malformed inner-class attribute:",
                      "\touterTypeInternal: " + outerName,
                      "\tinnerTypeInternal: " + name,
                      "\tinnerName: " + innerName),
                  origin));
        }
      }
      innerClasses.add(
          new InnerClassAttribute(
              access,
              application.getTypeFromName(name),
              outerName == null ? null : application.getTypeFromName(outerName),
              innerName == null ? null : application.getString(innerName)));
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
      // This is called for anonymous and local inner classes defined in classes or in methods.
      assert enclosingMember == null;
      DexType ownerType = application.getTypeFromName(owner);
      enclosingMember =
          name == null || name.equals("<clinit>")
              ? new EnclosingMethodAttribute(ownerType)
              : new EnclosingMethodAttribute(application.getMethod(ownerType, name, desc));
    }

    @Override
    public void visitNestHost(String nestHost) {
      assert this.nestHost == null && nestMembers.isEmpty();
      DexType nestHostType = application.getTypeFromName(nestHost);
      this.nestHost = new NestHostClassAttribute(nestHostType);
    }

    @Override
    public void visitNestMember(String nestMember) {
      assert nestHost == null;
      DexType nestMemberType = application.getTypeFromName(nestMember);
      nestMembers.add(new NestMemberClassAttribute(nestMemberType));
    }

    private String illegalClassFilePrefix(ClassAccessFlags accessFlags, String name) {
      return "Illegal class file: "
          + (accessFlags.isInterface() ? "Interface" : "Class")
          + " "
          + name;
    }

    private String illegalClassFilePostfix(CfVersion version) {
      return "Class file version " + version;
    }

    private String illegalClassFileMessage(
        ClassAccessFlags accessFlags, String name, CfVersion version, String message) {
      return illegalClassFilePrefix(accessFlags, name)
          + " " + message
          + ". " + illegalClassFilePostfix(version) + ".";
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(
        String name, String descriptor, String signature) {
      assert name != null;
      assert descriptor != null;
      return new CreateRecordComponentVisitor(this, name, descriptor, signature);
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
      assert permittedSubclass != null;
      DexType permittedSubclassType = application.getTypeFromName(permittedSubclass);
      permittedSubclasses.add(new PermittedSubclassAttribute(permittedSubclassType));
    }

    @Override
    public void visit(
        int rawVersion,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      version = CfVersion.fromRaw(rawVersion);
      if (InternalOptions.SUPPORTED_CF_VERSION.isLessThan(version)) {
        throw new CompilationError("Unsupported class file version: " + version, origin);
      }
      this.deprecated = AsmUtils.isDeprecated(access);
      accessFlags = ClassAccessFlags.fromCfAccessFlags(cleanAccessFlags(access));
      type = application.getTypeFromName(name);
      // Check if constraints from
      // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1 are met.
      if (!accessFlags.areValid(version, name.endsWith("/package-info"))) {
        throw new CompilationError(
            illegalClassFileMessage(
                accessFlags,
                name,
                version,
                "has invalid access flags. Found: " + accessFlags.toString()),
            origin);
      }
      if (superName == null && !name.equals(Constants.JAVA_LANG_OBJECT_NAME)) {
        throw new CompilationError(
            illegalClassFileMessage(accessFlags, name, version, "is missing a super type"), origin);
      }
      if (accessFlags.isInterface()
          && !Objects.equals(superName, Constants.JAVA_LANG_OBJECT_NAME)) {
        throw new CompilationError(
            illegalClassFileMessage(
                accessFlags,
                name,
                version,
                "must extend class java.lang.Object. Found: " + superName),
            origin);
      }
      checkName(name);
      assert superName != null || name.equals(Constants.JAVA_LANG_OBJECT_NAME);
      superType = superName == null ? null : application.getTypeFromName(superName);
      this.interfaces = application.getTypeListFromNames(interfaces);
      if (application.options.parseSignatureAttribute()) {
        classSignature =
            GenericSignature.parseClassSignature(
                name, signature, origin, application.getFactory(), application.options.reporter);
      }
    }

    @Override
    public void visitSource(String source, String debug) {
      if (source != null) {
        sourceFile = application.getString(source);
      }
      if (debug != null) {
        getAnnotations().add(
            DexAnnotation.createSourceDebugExtensionAnnotation(
                new DexValueString(application.getString(debug)), application.getFactory()));
      }
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String desc, String signature, Object value) {
      if (classKind == ClassKind.LIBRARY) {
        FieldAccessFlags flags = createFieldAccessFlags(access);
        if (flags.isPrivate()) {
          return null;
        }
      }
      checkName(name);
      return new CreateFieldVisitor(
          this, access, name, desc, signature, classKind == ClassKind.LIBRARY ? null : value);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      if (classKind == ClassKind.LIBRARY) {
        MethodAccessFlags flags = createMethodAccessFlags(name, access);
        if ((flags.isStatic() && flags.isConstructor()) || flags.isPrivate()) {
          return null;
        }
      }
      checkName(name);
      return new CreateMethodVisitor(access, name, desc, signature, exceptions, this);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(
          desc, visible, getAnnotations(), application, DexAnnotation::new);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      return createTypeAnnotationVisitor(
          desc, visible, getAnnotations(), application, typeRef, typePath);
    }

    @Override
    public void visitEnd() {
      if (defaultAnnotations != null) {
        addAnnotation(DexAnnotation.createAnnotationDefaultAnnotation(
            type, defaultAnnotations, application.getFactory()));
      }
      checkReachabilitySensitivity();
      checkRecord();
      T clazz =
          classKind.create(
              type,
              Kind.CF,
              origin,
              accessFlags,
              superType,
              interfaces,
              sourceFile,
              nestHost,
              nestMembers,
              permittedSubclasses,
              recordComponents,
              enclosingMember,
              innerClasses,
              classSignature,
              createAnnotationSet(annotations, application.options),
              staticFields.toArray(DexEncodedField.EMPTY_ARRAY),
              instanceFields.toArray(DexEncodedField.EMPTY_ARRAY),
              directMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
              virtualMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
              application.getFactory().getSkipNameValidationForTesting(),
              getChecksumSupplier(classKind),
              syntheticMarker);
      application.checkClassForMethodHandlesLookup(clazz, classKind);
      InnerClassAttribute innerClassAttribute = clazz.getInnerClassAttributeForThisClass();
      // A member class should not be a local or anonymous class.
      if (innerClassAttribute != null && innerClassAttribute.getOuter() != null) {
        if (innerClassAttribute.isAnonymous()) {
          assert innerClassAttribute.getInnerName() == null;
          // If the enclosing member is not null, the intention would be an anonymous class, and
          // thus the outer-class reference should have been null.  If the enclosing member is null,
          // it is likely due to the missing enclosing member.  In either case, we can recover
          // InnerClasses attribute by erasing the outer-class reference.
          InnerClassAttribute recoveredAttribute = new InnerClassAttribute(
              innerClassAttribute.getAccess(), innerClassAttribute.getInner(), null, null);
          clazz.replaceInnerClassAttributeForThisClass(recoveredAttribute);
        } else if (enclosingMember != null) {
          assert innerClassAttribute.isNamed();
          // It is unclear whether the intention was a member class or a local class. Fail hard.
          throw new CompilationError(
              StringUtils.lines(
                  "A member class cannot also be a (non-member) local class at the same time.",
                  "This is likely due to invalid EnclosingMethod and InnerClasses attributes:",
                  enclosingMember.toString(),
                  innerClassAttribute.toString()),
              origin);
        }
      }
      if (enclosingMember == null
          && (clazz.isLocalClass() || clazz.isAnonymousClass())
          && CfVersion.V1_6.isLessThan(version)) {
        application.options.warningMissingEnclosingMember(clazz.type, clazz.origin, version);
      }
      if (!clazz.isLibraryClass()) {
        context.owner = clazz;
      }
      if (clazz.isProgramClass()) {
        DexProgramClass programClass = clazz.asProgramClass();
        programClass.setInitialClassFileVersion(version);
        if (deprecated) {
          programClass.setDeprecated();
        }
      }
      classConsumer.accept(clazz);
    }

    private void checkRecord() {
      if (!accessFlags.isRecord()) {
        return;
      }
      application.addRecordWitness(type, classKind);
      if (classKind != ClassKind.PROGRAM) {
        // Non program classes may just be headers, in which case instance fields and annotations
        // may be partially stripped, leading to non matching record components and instance fields.
        // Record desugaring does not need information beyond the record flag on non program class,
        // so it's safe to compile even if there is a missmatch.
        return;
      }
      // TODO(b/274888318): Change this logic if we start stripping the record components.
      // Another approach would be to mark a bit in fields that are record components instead.
      String message = "Records are expected to have one record component per instance field.";
      if (recordComponents.size() != instanceFields.size()) {
        throw new CompilationError(message, origin);
      }
    }

    private ChecksumSupplier getChecksumSupplier(ClassKind<T> classKind) {
      if (application.options.encodeChecksums && classKind == ClassKind.PROGRAM) {
        CRC32 crc = new CRC32();
        crc.update(this.context.classCache, 0, this.context.classCache.length);
        final long value = crc.getValue();
        return clazz -> value;
      }
      return DexProgramClass::invalidChecksumRequest;
    }

    private void checkName(String name) {
      if (!application.options.canUseSpacesInSimpleName()
          && !DexString.isValidSimpleName(application.options.getMinApiLevel(), name)) {
        throw new CompilationError("Space characters in SimpleName '"
          + name + "' are not allowed prior to DEX version 040");
      }
    }

    // If anything is marked reachability sensitive, all methods need to be parsed including
    // locals information. This propagates the reachability sensitivity bit so that if any field
    // or method is annotated, all methods get parsed with locals information.
    private void checkReachabilitySensitivity() {
      if (hasReachabilitySensitiveMethod || hasReachabilitySensitiveField()) {
        for (DexEncodedMethod method : Iterables.concat(directMethods, virtualMethods)) {
          Code code = method.getCode();
          if (code != null && code.isCfCode()) {
            code.asLazyCfCode().markReachabilitySensitive();
          }
        }
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean hasReachabilitySensitiveField() {
      DexType reachabilitySensitive = application.getFactory().annotationReachabilitySensitive;
      for (DexEncodedField field : Iterables.concat(instanceFields, staticFields)) {
        for (DexAnnotation annotation : field.annotations().annotations) {
          if (annotation.annotation.type == reachabilitySensitive) {
            return true;
          }
        }
      }
      return false;
    }

    private void addDefaultAnnotation(String name, DexValue value) {
      if (defaultAnnotations == null) {
        defaultAnnotations = new ArrayList<>();
      }
      defaultAnnotations.add(new DexAnnotationElement(application.getString(name), value));
    }

    private void addAnnotation(DexAnnotation annotation) {
      getAnnotations().add(annotation);
    }

    private List<DexAnnotation> getAnnotations() {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      return annotations;
    }

    public boolean isInANest() {
      return !nestMembers.isEmpty() || nestHost != null;
    }
  }

  private static DexAnnotationSet createAnnotationSet(
      List<DexAnnotation> annotations, InternalOptions options) {
    if (annotations == null || annotations.isEmpty()) {
      return DexAnnotationSet.empty();
    }
    if (options.isGeneratingDex()) {
      DexType dupType = DexAnnotationSet.findDuplicateEntryType(annotations);
      if (dupType != null) {
        throw new CompilationError(
            "Multiple annotations of type `" + dupType.toSourceString() + "`");
      }
    }
    return DexAnnotationSet.create(annotations.toArray(DexAnnotation.EMPTY_ARRAY));
  }

  private static class CreateFieldVisitor extends FieldVisitor {

    private final CreateDexClassVisitor<?> parent;
    private final int access;
    private final String name;
    private final String desc;
    private final Object value;
    private final FieldTypeSignature fieldSignature;
    private List<DexAnnotation> annotations = null;

    public CreateFieldVisitor(
        CreateDexClassVisitor<?> parent,
        int access,
        String name,
        String desc,
        String signature,
        Object value) {
      super(ASM_VERSION);
      this.parent = parent;
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.value = value;
      this.fieldSignature =
          parent.application.options.parseSignatureAttribute()
              ? GenericSignature.parseFieldTypeSignature(
                  name,
                  signature,
                  parent.origin,
                  parent.application.getFactory(),
                  parent.application.options.reporter)
              : FieldTypeSignature.noSignature();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(
          desc, visible, getAnnotations(), parent.application, DexAnnotation::new);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      return createTypeAnnotationVisitor(
          desc, visible, getAnnotations(), parent.application, typeRef, typePath);
    }

    @Override
    public void visitEnd() {
      FieldAccessFlags flags = createFieldAccessFlags(access);
      DexField dexField = parent.application.getField(parent.type, name, desc);
      parent.application.checkFieldForRecord(dexField, parent.classKind);
      parent.application.checkFieldForMethodHandlesLookup(dexField, parent.classKind);
      parent.application.checkFieldForVarHandle(dexField, parent.classKind);
      Wrapper<DexField> signature = FieldSignatureEquivalence.get().wrap(dexField);
      if (parent.fieldSignatures.add(signature)) {
        DexAnnotationSet annotationSet =
            createAnnotationSet(annotations, parent.application.options);
        DexValue staticValue = flags.isStatic() ? getStaticValue(value, dexField.type) : null;
        DexEncodedField field =
            DexEncodedField.builder()
                .setField(dexField)
                .setAccessFlags(flags)
                .setGenericSignature(fieldSignature)
                .setAnnotations(annotationSet)
                .setStaticValue(staticValue)
                .setDeprecated(AsmUtils.isDeprecated(access))
                .disableAndroidApiLevelCheck()
                .build();
        if (flags.isStatic()) {
          parent.staticFields.add(field);
        } else {
          parent.instanceFields.add(field);
        }
      } else {
        parent.application.options.reporter.warning(
            new StringDiagnostic(
                String.format("Field `%s` has multiple definitions", dexField.toSourceString())));
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private DexValue getStaticValue(Object value, DexType type) {
      if (value == null) {
        return null;
      }
      DexItemFactory factory = parent.application.getFactory();
      if (type == factory.booleanType) {
        int i = (Integer) value;
        assert 0 <= i && i <= 1;
        return DexValueBoolean.create(i == 1);
      }
      if (type == factory.byteType) {
        return DexValueByte.create(((Integer) value).byteValue());
      }
      if (type == factory.shortType) {
        return DexValueShort.create(((Integer) value).shortValue());
      }
      if (type == factory.charType) {
        return DexValueChar.create((char) ((Integer) value).intValue());
      }
      if (type == factory.intType) {
        return DexValueInt.create((Integer) value);
      }
      if (type == factory.floatType) {
        return DexValueFloat.create((Float) value);
      }
      if (type == factory.longType) {
        return DexValueLong.create((Long) value);
      }
      if (type == factory.doubleType) {
        return DexValueDouble.create((Double) value);
      }
      if (type == factory.stringType) {
        return new DexValueString(factory.createString((String) value));
      }
      throw new Unreachable("Unexpected static-value type " + type);
    }

    private List<DexAnnotation> getAnnotations() {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      return annotations;
    }
  }

  private static class CreateMethodVisitor extends MethodVisitor {

    private final String name;
    final CreateDexClassVisitor<?> parent;
    private final int parameterCount;
    private List<DexAnnotation> annotations = null;
    private DexValue defaultAnnotation = null;
    private int annotableParameterCount = -1;
    private List<List<DexAnnotation>> parameterAnnotationsLists = null;
    private List<DexValue> parameterNames = null;
    private List<DexValue> parameterFlags = null;
    private final MethodTypeSignature genericSignature;
    final DexMethod method;
    final MethodAccessFlags flags;
    final boolean deprecated;
    Code code = null;

    public CreateMethodVisitor(
        int access,
        String name,
        String desc,
        String signature,
        String[] exceptions,
        CreateDexClassVisitor<?> parent) {
      super(ASM_VERSION);
      this.name = name;
      this.parent = parent;
      this.method = parent.application.getMethod(parent.type, name, desc);
      this.flags = createMethodAccessFlags(name, access);
      this.deprecated = AsmUtils.isDeprecated(access);
      parameterCount = DescriptorUtils.getArgumentCount(desc);
      if (exceptions != null && exceptions.length > 0) {
        DexValue[] values = new DexValue[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
          values[i] = new DexValueType(parent.application.getTypeFromName(exceptions[i]));
        }
        addAnnotation(DexAnnotation.createThrowsAnnotation(
            values, parent.application.getFactory()));
      }
      genericSignature =
          parent.application.options.parseSignatureAttribute()
              ? GenericSignature.parseMethodSignature(
                  name,
                  signature,
                  parent.origin,
                  parent.application.getFactory(),
                  parent.application.options.reporter)
              : MethodTypeSignature.noSignature();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(
          desc, visible, getAnnotations(), parent.application, DexAnnotation::new);
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new CreateAnnotationVisitor(parent.application, (names, elements) -> {
        assert elements.size() == 1;
        defaultAnnotation = elements.get(0);
      });
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
      if (annotableParameterCount != -1) {
        // TODO(113565942): We assume that the runtime visible and runtime invisible parameter
        // count is always the same. It doesn't have to be according to the classfile format, so
        // we really should split it into two sets.
        assert annotableParameterCount == parameterCount;
      }
      annotableParameterCount = parameterCount;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      if (parameterAnnotationsLists == null) {
        if (annotableParameterCount == -1) {
          annotableParameterCount = parameterCount;
        }
        parameterAnnotationsLists = new ArrayList<>(annotableParameterCount);
        for (int i = 0; i < annotableParameterCount; i++) {
          parameterAnnotationsLists.add(new ArrayList<>());
        }
      }
      assert mv == null;
      return createAnnotationVisitor(
          desc,
          visible,
          parameterAnnotationsLists.get(parameter),
          parent.application,
          DexAnnotation::new);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      return createTypeAnnotationVisitor(
          desc, visible, getAnnotations(), parent.application, typeRef, typePath);
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // We do not support code type annotations since that would require us to maintain these
      // through IR where we may as well invalidate any assumptions made on the code.
      return null;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath,
        Label[] start, Label[] end, int[] index, String desc, boolean visible) {
      // We do not support code type annotations since that would require us to maintain these
      // through IR where we may as well invalidate any assumptions made on the code.
      return null;
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // We do not support code type annotations since that would require us to maintain these
      // through IR where we may as well invalidate any assumptions made on the code.
      return null;
    }

    @Override
    public void visitParameter(String name, int access) {
      if (parameterNames == null) {
        assert parameterFlags == null;
        parameterNames = new ArrayList<>(parameterCount);
        parameterFlags = new ArrayList<>(parameterCount);
      }
      if (name == null) {
        // The JVM spec defines a null entry as valid and indicating no parameter name.
        // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.24
        parameterNames.add(DexValueNull.NULL);
      } else {
        parameterNames.add(new DexValueString(parent.application.getFactory().createString(name)));
      }
      parameterFlags.add(DexValueInt.create(access));
      super.visitParameter(name, access);
    }

    @Override
    public void visitCode() {
      throw new Unreachable("visitCode() should not be called when SKIP_CODE is set");
    }

    private boolean classRequiresCode() {
      return parent.classKind == ClassKind.PROGRAM
          || (!parent.application.options.canUseNestBasedAccess()
              && parent.classKind == ClassKind.CLASSPATH
              && parent.isInANest());
    }

    @Override
    public void visitEnd() {
      InternalOptions options = parent.application.options;
      parent.application.checkMethodForRecord(method, parent.classKind);
      parent.application.checkMethodForMethodHandlesLookup(method, parent.classKind);
      parent.application.checkMethodForVarHandle(method, parent.classKind);
      if (!flags.isAbstract() && !flags.isNative() && classRequiresCode()) {
        code = new LazyCfCode(parent.origin, parent.context, parent.application);
      }
      ParameterAnnotationsList parameterAnnotationsList;
      if (parameterAnnotationsLists == null) {
        parameterAnnotationsList = ParameterAnnotationsList.empty();
      } else {
        DexAnnotationSet[] sets = new DexAnnotationSet[parameterAnnotationsLists.size()];
        for (int i = 0; i < parameterAnnotationsLists.size(); i++) {
          sets[i] = createAnnotationSet(parameterAnnotationsLists.get(i), options);
        }
        parameterAnnotationsList = ParameterAnnotationsList.create(sets);
      }
      if (parameterNames != null) {
        assert parameterFlags != null;
        if (parameterNames.size() != parameterCount) {
          options.warningInvalidParameterAnnotations(
              method, parent.origin, parameterCount, parameterNames.size());
        }
        getAnnotations().add(DexAnnotation.createMethodParametersAnnotation(
            parameterNames.toArray(DexValue.EMPTY_ARRAY),
            parameterFlags.toArray(DexValue.EMPTY_ARRAY),
            parent.application.getFactory()));
      }
      DexEncodedMethod dexMethod =
          DexEncodedMethod.builder()
              .setMethod(method)
              .setAccessFlags(flags)
              .setGenericSignature(genericSignature)
              .setAnnotations(createAnnotationSet(annotations, options))
              .setParameterAnnotations(parameterAnnotationsList)
              .setCode(code)
              .setClassFileVersion(parent.version)
              .setDeprecated(deprecated)
              .disableParameterAnnotationListCheck()
              .disableAndroidApiLevelCheck()
              .build();
      Wrapper<DexMethod> signature = MethodSignatureEquivalence.get().wrap(method);
      if (parent.methodSignatures.add(signature)) {
        parent.hasReachabilitySensitiveMethod |= isReachabilitySensitive();
        if (flags.isStatic() || flags.isConstructor() || flags.isPrivate()) {
          parent.directMethods.add(dexMethod);
        } else {
          parent.virtualMethods.add(dexMethod);
        }
      } else {
        options.reporter.warning(
            new StringDiagnostic(
                String.format(
                    "Ignoring an implementation of the method `%s` because it has multiple "
                        + "definitions",
                    method.toSourceString())));
      }
      if (defaultAnnotation != null) {
        parent.addDefaultAnnotation(name, defaultAnnotation);
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isReachabilitySensitive() {
      DexType reachabilitySensitive =
          parent.application.getFactory().annotationReachabilitySensitive;
      for (DexAnnotation annotation : getAnnotations()) {
        if (annotation.annotation.type == reachabilitySensitive) {
          return true;
        }
      }
      return false;
    }

    private List<DexAnnotation> getAnnotations() {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      return annotations;
    }

    private void addAnnotation(DexAnnotation annotation) {
      getAnnotations().add(annotation);
    }
  }

  private static class CreateAnnotationVisitor extends AnnotationVisitor {

    private final JarApplicationReader application;
    private final BiConsumer<List<DexString>, List<DexValue>> onVisitEnd;
    private List<DexString> names = null;
    private final List<DexValue> values = new ArrayList<>();

    public CreateAnnotationVisitor(
        JarApplicationReader application, BiConsumer<List<DexString>, List<DexValue>> onVisitEnd) {
      super(ASM_VERSION);
      this.application = application;
      this.onVisitEnd = onVisitEnd;
    }

    @Override
    public void visit(String name, Object value) {
      addElement(name, getDexValue(value));
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      DexType owner = application.getTypeFromDescriptor(desc);
      addElement(name, new DexValueEnum(application.getField(owner, value, desc)));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      return new CreateAnnotationVisitor(application, (names, values) ->
          addElement(name, new DexValueAnnotation(
              createEncodedAnnotation(desc, names, values, application))));
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return new CreateAnnotationVisitor(application, (names, values) -> {
        assert names == null;
        addElement(name, new DexValueArray(values.toArray(DexValue.EMPTY_ARRAY)));
      });
    }

    @Override
    public void visitEnd() {
      onVisitEnd.accept(names, values);
    }

    private void addElement(String name, DexValue value) {
      if (name != null) {
        if (names == null) {
          names = new ArrayList<>();
        }
        names.add(application.getString(name));
      }
      values.add(value);
    }

    private static DexValueArray getDexValueArray(Object value) {
      if (value instanceof byte[]) {
        byte[] values = (byte[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueByte.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof boolean[]) {
        boolean[] values = (boolean[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueBoolean.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof char[]) {
        char[] values = (char[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueChar.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof short[]) {
        short[] values = (short[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueShort.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof int[]) {
        int[] values = (int[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueInt.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof long[]) {
        long[] values = (long[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueLong.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof float[]) {
        float[] values = (float[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueFloat.create(values[i]);
        }
        return new DexValueArray(elements);
      } else if (value instanceof double[]) {
        double[] values = (double[]) value;
        DexValue[] elements = new DexValue[values.length];
        for (int i = 0; i < values.length; i++) {
          elements[i] = DexValueDouble.create(values[i]);
        }
        return new DexValueArray(elements);
      }
      throw new Unreachable("Unexpected type of annotation value: " + value);
    }

    private DexValue getDexValue(Object value) {
      if (value == null) {
        return DexValueNull.NULL;
      }
      if (value instanceof Byte) {
        return DexValueByte.create((Byte) value);
      } else if (value instanceof Boolean) {
        return DexValueBoolean.create((Boolean) value);
      } else if (value instanceof Character) {
        return DexValueChar.create((Character) value);
      } else if (value instanceof Short) {
        return DexValueShort.create((Short) value);
      } else if (value instanceof Integer) {
        return DexValueInt.create((Integer) value);
      } else if (value instanceof Long) {
        return DexValueLong.create((Long) value);
      } else if (value instanceof Float) {
        return DexValueFloat.create((Float) value);
      } else if (value instanceof Double) {
        return DexValueDouble.create((Double) value);
      } else if (value instanceof String) {
        return new DexValueString(application.getString((String) value));
      } else if (value instanceof Type) {
        return new DexValueType(application.getTypeFromDescriptor(((Type) value).getDescriptor()));
      }
      return getDexValueArray(value);
    }
  }

  private static class CreateRecordComponentVisitor extends RecordComponentVisitor {
    private final CreateDexClassVisitor<?> parent;
    private final String name;
    private final String descriptor;
    private final DexField field;
    private final FieldTypeSignature componentSignature;

    public CreateRecordComponentVisitor(
        CreateDexClassVisitor<?> parent, String name, String descriptor, String signature) {
      super(ASM_VERSION);
      this.field = parent.application.getField(parent.type, name, descriptor);
      this.parent = parent;
      this.name = name;
      this.descriptor = descriptor;
      this.componentSignature =
          parent.application.options.parseSignatureAttribute()
              ? GenericSignature.parseFieldTypeSignature(
                  name,
                  signature,
                  parent.origin,
                  parent.application.getFactory(),
                  parent.application.options.reporter)
              : FieldTypeSignature.noSignature();
    }

    private List<DexAnnotation> annotations = null;

    private List<DexAnnotation> getAnnotations() {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      return annotations;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(
          desc, visible, getAnnotations(), parent.application, DexAnnotation::new);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      // Java 8 type annotations are not supported by DEX, thus ignore them.
      return null;
    }

    @Override
    public void visitEnd() {
      assert annotations == null || !annotations.isEmpty();
      parent.recordComponents.add(
          new RecordComponentInfo(
              field,
              componentSignature,
              annotations != null && !annotations.isEmpty()
                  ? annotations
                  : Collections.emptyList()));
    }
  }

  public static class ReparseContext {

    // This will hold the content of the whole class. Once all the methods of the class are swapped
    // from this to the actual JarCode, no other references would be left and the content can be
    // GC'd.
    public byte[] classCache;
    public DexClass owner;
    public final List<Code> codeList = new ArrayList<>();
  }
}
