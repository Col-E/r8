// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_DEPRECATED;
import static org.objectweb.asm.Opcodes.ASM6;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfBinop;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstMethodHandle;
import com.android.tools.r8.cf.code.CfConstMethodType;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfMultiANewArray;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfNop;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.CfUnop;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
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
import com.android.tools.r8.graph.JarCode.ReparseContext;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * Java/Jar class reader for constructing dex/graph structure.
 */
public class JarClassFileReader {

  private static final byte[] CLASSFILE_HEADER = ByteBuffer.allocate(4).putInt(0xCAFEBABE).array();

  // Hidden ASM "synthetic attribute" bit we need to clear.
  private static final int ACC_SYNTHETIC_ATTRIBUTE = 0x40000;
  // Descriptor used by ASM for missing annotations.
  public static final String SYNTHETIC_ANNOTATION = "Ljava/lang/Synthetic;";

  private final JarApplicationReader application;
  private final Consumer<DexClass> classConsumer;

  public JarClassFileReader(
      JarApplicationReader application, Consumer<DexClass> classConsumer) {
    this.application = application;
    this.classConsumer = classConsumer;
  }

  public void read(Origin origin, ClassKind classKind, InputStream input) throws IOException {
    if (!input.markSupported()) {
      input = new BufferedInputStream(input);
    }
    byte[] header = new byte[CLASSFILE_HEADER.length];
    input.mark(header.length);
    int size = 0;
    while (size < header.length) {
      int read = input.read(header, size, header.length - size);
      if (read < 0) {
        throw new CompilationError("Invalid empty classfile", origin);
      }
      size += read;
    }
    if (!Arrays.equals(CLASSFILE_HEADER, header)) {
      throw new CompilationError("Invalid classfile header", origin);
    }
    input.reset();

    ClassReader reader = new ClassReader(input);
    int flags = SKIP_FRAMES;
    if (application.options.enableCfFrontend) {
      flags = EXPAND_FRAMES;
    }
    reader.accept(
        new CreateDexClassVisitor(origin, classKind, reader.b, application, classConsumer), flags);
  }

  private static int cleanAccessFlags(int access) {
    // Clear the "synthetic attribute" and "deprecated" attribute-flags if present.
    return access & ~ACC_SYNTHETIC_ATTRIBUTE & ~ACC_DEPRECATED;
  }

  public static MethodAccessFlags createMethodAccessFlags(String name, int access) {
    boolean isConstructor =
        name.equals(Constants.INSTANCE_INITIALIZER_NAME)
            || name.equals(Constants.CLASS_INITIALIZER_NAME);
    return MethodAccessFlags.fromCfAccessFlags(cleanAccessFlags(access), isConstructor);
  }

  private static AnnotationVisitor createAnnotationVisitor(String desc, boolean visible,
      List<DexAnnotation> annotations,
      JarApplicationReader application) {
    assert annotations != null;
    int visiblity = visible ? DexAnnotation.VISIBILITY_RUNTIME : DexAnnotation.VISIBILITY_BUILD;
    return new CreateAnnotationVisitor(application, (names, values) ->
        annotations.add(new DexAnnotation(visiblity,
            createEncodedAnnotation(desc, names, values, application))));
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

  private static class CreateDexClassVisitor extends ClassVisitor {

    private final Origin origin;
    private final ClassKind classKind;
    private final JarApplicationReader application;
    private final Consumer<DexClass> classConsumer;
    private final ReparseContext context = new ReparseContext();

    // DexClass data.
    private int version;
    private DexType type;
    private ClassAccessFlags accessFlags;
    private DexType superType;
    private DexTypeList interfaces;
    private DexString sourceFile;
    private EnclosingMethodAttribute enclosingMember = null;
    private final List<InnerClassAttribute> innerClasses = new ArrayList<>();
    private List<DexAnnotation> annotations = null;
    private List<DexAnnotationElement> defaultAnnotations = null;
    private final List<DexEncodedField> staticFields = new ArrayList<>();
    private final List<DexEncodedField> instanceFields = new ArrayList<>();
    private final List<DexEncodedMethod> directMethods = new ArrayList<>();
    private final List<DexEncodedMethod> virtualMethods = new ArrayList<>();

    public CreateDexClassVisitor(
        Origin origin,
        ClassKind classKind,
        byte[] classCache,
        JarApplicationReader application,
        Consumer<DexClass> classConsumer) {
      super(ASM6);
      this.origin = origin;
      this.classKind = classKind;
      this.classConsumer = classConsumer;
      this.context.classCache = classCache;
      this.application = application;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
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
          name == null
              ? new EnclosingMethodAttribute(ownerType)
              : new EnclosingMethodAttribute(application.getMethod(ownerType, name, desc));
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
        String[] interfaces) {
      this.version = version;
      accessFlags = ClassAccessFlags.fromCfAccessFlags(cleanAccessFlags(access));
      type = application.getTypeFromName(name);
      // Check if constraints from
      // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1 are met.
      if (!accessFlags.areValid(getMajorVersion())) {
        throw new CompilationError("Illegal class file: Class " + name
            + " has invalid access flags. Found: " + accessFlags.toString(), origin);
      }
      if (superName == null && !name.equals(Constants.JAVA_LANG_OBJECT_NAME)) {
        throw new CompilationError("Illegal class file: Class " + name
            + " is missing a super type.", origin);
      }
      if (accessFlags.isInterface()
          && !Objects.equals(superName, Constants.JAVA_LANG_OBJECT_NAME)) {
        throw new CompilationError("Illegal class file: Interface " + name
            + " must extend class java.lang.Object. Found: " + Objects.toString(superName), origin);
      }
      assert superName != null || name.equals(Constants.JAVA_LANG_OBJECT_NAME);
      superType = superName == null ? null : application.getTypeFromName(superName);
      this.interfaces = application.getTypeListFromNames(interfaces);
      if (signature != null && !signature.isEmpty()) {
        addAnnotation(DexAnnotation.createSignatureAnnotation(signature, application.getFactory()));
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
      return new CreateFieldVisitor(this, access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      if (application.options.enableCfFrontend) {
        return new CfCreateMethodVisitor(access, name, desc, signature, exceptions, this);
      }
      return new CreateMethodVisitor(access, name, desc, signature, exceptions, this);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(desc, visible, getAnnotations(), application);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public void visitAttribute(Attribute attr) {
      // Unknown attribute must only be ignored
    }

    @Override
    public void visitEnd() {
      if (defaultAnnotations != null) {
        addAnnotation(DexAnnotation.createAnnotationDefaultAnnotation(
            type, defaultAnnotations, application.getFactory()));
      }
      DexClass clazz =
          classKind.create(
              type,
              Kind.CF,
              origin,
              accessFlags,
              superType,
              interfaces,
              sourceFile,
              enclosingMember,
              innerClasses,
              createAnnotationSet(annotations),
              staticFields.toArray(new DexEncodedField[staticFields.size()]),
              instanceFields.toArray(new DexEncodedField[instanceFields.size()]),
              directMethods.toArray(new DexEncodedMethod[directMethods.size()]),
              virtualMethods.toArray(new DexEncodedMethod[virtualMethods.size()]),
              application.getFactory().getSkipNameValidationForTesting());
      if (clazz.isProgramClass()) {
        context.owner = clazz.asProgramClass();
        clazz.asProgramClass().setClassFileVersion(version);
      }
      classConsumer.accept(clazz);
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

    private int getMajorVersion() {
      return version & 0xFFFF;
    }

    private int getMinorVersion() {
      return ((version >> 16) & 0xFFFF);
    }
  }

  private static DexAnnotationSet createAnnotationSet(List<DexAnnotation> annotations) {
    return annotations == null || annotations.isEmpty()
        ? DexAnnotationSet.empty()
        : new DexAnnotationSet(annotations.toArray(new DexAnnotation[annotations.size()]));
  }

  private static class CreateFieldVisitor extends FieldVisitor {

    private final CreateDexClassVisitor parent;
    private final int access;
    private final String name;
    private final String desc;
    private final Object value;
    private List<DexAnnotation> annotations = null;

    public CreateFieldVisitor(CreateDexClassVisitor parent,
        int access, String name, String desc, String signature, Object value) {
      super(ASM6);
      this.parent = parent;
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.value = value;
      if (signature != null && !signature.isEmpty()) {
        addAnnotation(DexAnnotation.createSignatureAnnotation(
            signature, parent.application.getFactory()));
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(desc, visible, getAnnotations(), parent.application);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public void visitEnd() {
      FieldAccessFlags flags = FieldAccessFlags.fromCfAccessFlags(cleanAccessFlags(access));
      DexField dexField = parent.application.getField(parent.type, name, desc);
      DexAnnotationSet annotationSet = createAnnotationSet(annotations);
      DexValue staticValue = flags.isStatic() ? getStaticValue(value, dexField.type) : null;
      DexEncodedField field = new DexEncodedField(dexField, flags, annotationSet, staticValue);
      if (flags.isStatic()) {
        parent.staticFields.add(field);
      } else {
        parent.instanceFields.add(field);
      }
    }

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

    private void addAnnotation(DexAnnotation annotation) {
      getAnnotations().add(annotation);
    }

    private List<DexAnnotation> getAnnotations() {
      if (annotations == null) {
        annotations = new ArrayList<>();
      }
      return annotations;
    }
  }

  private static class CreateMethodVisitor extends MethodVisitor {

    private final int access;
    private final String name;
    private final String desc;
    final CreateDexClassVisitor parent;
    private final int parameterCount;
    private List<DexAnnotation> annotations = null;
    private DexValue defaultAnnotation = null;
    private int fakeParameterAnnotations = 0;
    private List<List<DexAnnotation>> parameterAnnotations = null;
    private List<DexValue> parameterNames = null;
    private List<DexValue> parameterFlags = null;
    final DexMethod method;
    final MethodAccessFlags flags;
    Code code = null;

    public CreateMethodVisitor(int access, String name, String desc, String signature,
        String[] exceptions, CreateDexClassVisitor parent) {
      super(ASM6);
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.parent = parent;
      this.method = parent.application.getMethod(parent.type, name, desc);
      this.flags = createMethodAccessFlags(name, access);
      parameterCount = JarApplicationReader.getArgumentCount(desc);
      if (exceptions != null && exceptions.length > 0) {
        DexValue[] values = new DexValue[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
          values[i] = new DexValueType(parent.application.getTypeFromName(exceptions[i]));
        }
        addAnnotation(DexAnnotation.createThrowsAnnotation(
            values, parent.application.getFactory()));
      }
      if (signature != null && !signature.isEmpty()) {
        addAnnotation(DexAnnotation.createSignatureAnnotation(
            signature, parent.application.getFactory()));
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return createAnnotationVisitor(desc, visible, getAnnotations(), parent.application);
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      return new CreateAnnotationVisitor(parent.application, (names, elements) -> {
        assert elements.size() == 1;
        defaultAnnotation = elements.get(0);
      });
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      // ASM decided to workaround a javac bug that incorrectly deals with synthesized parameter
      // annotations. However, that leads us to have different behavior than javac+jvm and
      // dx+art. The workaround is to use a non-existing descriptor "Ljava/lang/Synthetic;" for
      // exactly this case. In order to remove the workaround we ignore all annotations
      // with that descriptor. If javac is fixed, the ASM workaround will not be hit and we will
      // never see this non-existing annotation descriptor. ASM uses the same check to make
      // sure to undo their workaround for the javac bug in their MethodWriter.
      if (desc.equals(SYNTHETIC_ANNOTATION)) {
        // We can iterate through all the parameters twice. Once for visible and once for
        // invisible parameter annotations. We only record the number of fake parameter
        // annotations once.
        if (parameterAnnotations == null) {
          fakeParameterAnnotations++;
        }
        return null;
      }
      if (parameterAnnotations == null) {
        int adjustedParameterCount = parameterCount - fakeParameterAnnotations;
        parameterAnnotations = new ArrayList<>(adjustedParameterCount);
        for (int i = 0; i < adjustedParameterCount; i++) {
          parameterAnnotations.add(new ArrayList<>());
        }
      }
      assert mv == null;
      return createAnnotationVisitor(desc, visible,
          parameterAnnotations.get(parameter - fakeParameterAnnotations), parent.application);
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath,
        Label[] start, Label[] end, int[] index, String desc, boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      // Java 8 type annotations are not supported by Dex, thus ignore them.
      return null;
    }

    @Override
    public void visitParameter(String name, int access) {
      if (parameterNames == null) {
        assert parameterFlags == null;
        parameterNames = new ArrayList<>(parameterCount);
        parameterFlags = new ArrayList<>(parameterCount);
      }
      parameterNames.add(new DexValueString(parent.application.getFactory().createString(name)));
      parameterFlags.add(DexValueInt.create(access));
      super.visitParameter(name, access);
    }

    @Override
    public void visitCode() {
      assert !flags.isAbstract() && !flags.isNative();
      if (parent.classKind == ClassKind.PROGRAM) {
        code = new JarCode(method, parent.origin, parent.context, parent.application);
      }
    }

    @Override
    public void visitEnd() {
      assert flags.isAbstract() || flags.isNative() || parent.classKind != ClassKind.PROGRAM
          || code != null;
      DexAnnotationSetRefList parameterAnnotationSets;
      if (parameterAnnotations == null) {
        parameterAnnotationSets = DexAnnotationSetRefList.empty();
      } else {
        DexAnnotationSet[] sets = new DexAnnotationSet[parameterAnnotations.size()];
        for (int i = 0; i < parameterAnnotations.size(); i++) {
          sets[i] = createAnnotationSet(parameterAnnotations.get(i));
        }
        parameterAnnotationSets = new DexAnnotationSetRefList(sets, fakeParameterAnnotations);
      }
      InternalOptions internalOptions = parent.application.options;
      if (parameterNames != null && internalOptions.canUseParameterNameAnnotations()) {
        assert parameterFlags != null;
        if (parameterNames.size() != parameterCount) {
          internalOptions.warningInvalidParameterAnnotations(
              method, parent.origin, parameterCount, parameterNames.size());
        }
        getAnnotations().add(DexAnnotation.createMethodParametersAnnotation(
            parameterNames.toArray(new DexValue[parameterNames.size()]),
            parameterFlags.toArray(new DexValue[parameterFlags.size()]),
            parent.application.getFactory()));
      }
      DexEncodedMethod dexMethod = new DexEncodedMethod(method, flags,
          createAnnotationSet(annotations), parameterAnnotationSets, code);
      if (flags.isStatic() || flags.isConstructor() || flags.isPrivate()) {
        parent.directMethods.add(dexMethod);
      } else {
        parent.virtualMethods.add(dexMethod);
      }
      if (defaultAnnotation != null) {
        parent.addDefaultAnnotation(name, defaultAnnotation);
      }
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

  private static class CfCreateMethodVisitor extends CreateMethodVisitor {

    private final JarApplicationReader application;
    private final DexItemFactory factory;
    private int maxStack;
    private int maxLocals;
    private List<CfInstruction> instructions;
    private List<CfTryCatch> tryCatchRanges;
    private List<LocalVariableInfo> localVariables;
    private Map<Label, CfLabel> labelMap;

    public CfCreateMethodVisitor(
        int access,
        String name,
        String desc,
        String signature,
        String[] exceptions,
        CreateDexClassVisitor parent) {
      super(access, name, desc, signature, exceptions, parent);
      this.application = parent.application;
      this.factory = application.getFactory();
    }

    @Override
    public void visitCode() {
      assert !flags.isAbstract() && !flags.isNative();
      maxStack = 0;
      maxLocals = 0;
      instructions = new ArrayList<>();
      tryCatchRanges = new ArrayList<>();
      localVariables = new ArrayList<>();
      labelMap = new IdentityHashMap<>();
    }

    @Override
    public void visitEnd() {
      if (!flags.isAbstract() && !flags.isNative() && parent.classKind == ClassKind.PROGRAM) {
        code =
            new CfCode(method, maxStack, maxLocals, instructions, tryCatchRanges, localVariables);
      }
      super.visitEnd();
    }

    @Override
    public void visitFrame(
        int frameType, int nLocals, Object[] localTypes, int nStack, Object[] stackTypes) {
      assert frameType == Opcodes.F_NEW;
      Int2ReferenceSortedMap<FrameType> parsedLocals = parseLocals(nLocals, localTypes);
      List<FrameType> parsedStack = parseStack(nStack, stackTypes);
      instructions.add(new CfFrame(parsedLocals, parsedStack));
    }

    private Int2ReferenceSortedMap<FrameType> parseLocals(int typeCount, Object[] asmTypes) {
      Int2ReferenceSortedMap<FrameType> types = new Int2ReferenceAVLTreeMap<>();
      int i = 0;
      for (int j = 0; j < typeCount; j++) {
        Object localType = asmTypes[j];
        FrameType value = getFrameType(localType);
        types.put(i++, value);
        if (value.isWide()) {
          i++;
        }
      }
      return types;
    }

    private List<FrameType> parseStack(int nStack, Object[] stackTypes) {
      List<FrameType> dexStack = new ArrayList<>(nStack);
      for (int i = 0; i < nStack; i++) {
        dexStack.add(getFrameType(stackTypes[i]));
      }
      return dexStack;
    }

    private FrameType getFrameType(Object localType) {
      if (localType instanceof Label) {
        return FrameType.uninitializedNew(getLabel((Label) localType));
      } else if (localType == Opcodes.UNINITIALIZED_THIS) {
        return FrameType.uninitializedThis();
      } else if (localType == null || localType == Opcodes.TOP) {
        return FrameType.top();
      } else {
        return FrameType.initialized(parseAsmType(localType));
      }
    }

    private CfLabel getLabel(Label label) {
      return labelMap.computeIfAbsent(label, l -> new CfLabel());
    }

    private DexType parseAsmType(Object local) {
      assert local != null && local != Opcodes.TOP;
      if (local == Opcodes.INTEGER) {
        return factory.intType;
      } else if (local == Opcodes.FLOAT) {
        return factory.floatType;
      } else if (local == Opcodes.LONG) {
        return factory.longType;
      } else if (local == Opcodes.DOUBLE) {
        return factory.doubleType;
      } else if (local == Opcodes.NULL) {
        return DexItemFactory.nullValueType;
      } else if (local instanceof String) {
        return createTypeFromInternalType((String) local);
      } else {
        throw new Unreachable("Unexpected ASM type: " + local);
      }
    }

    private DexType createTypeFromInternalType(String local) {
      assert local.indexOf('.') == -1;
      return factory.createType("L" + local + ";");
    }

    @Override
    public void visitInsn(int opcode) {
      switch (opcode) {
        case Opcodes.NOP:
          instructions.add(new CfNop());
          break;
        case Opcodes.ACONST_NULL:
          instructions.add(new CfConstNull());
          break;
        case Opcodes.ICONST_M1:
        case Opcodes.ICONST_0:
        case Opcodes.ICONST_1:
        case Opcodes.ICONST_2:
        case Opcodes.ICONST_3:
        case Opcodes.ICONST_4:
        case Opcodes.ICONST_5:
          instructions.add(new CfConstNumber(opcode - Opcodes.ICONST_0, ValueType.INT));
          break;
        case Opcodes.LCONST_0:
        case Opcodes.LCONST_1:
          instructions.add(new CfConstNumber(opcode - Opcodes.LCONST_0, ValueType.LONG));
          break;
        case Opcodes.FCONST_0:
        case Opcodes.FCONST_1:
        case Opcodes.FCONST_2:
          instructions.add(
              new CfConstNumber(
                  Float.floatToRawIntBits(opcode - Opcodes.FCONST_0), ValueType.FLOAT));
          break;
        case Opcodes.DCONST_0:
        case Opcodes.DCONST_1:
          instructions.add(
              new CfConstNumber(
                  Double.doubleToRawLongBits(opcode - Opcodes.DCONST_0), ValueType.DOUBLE));
          break;
        case Opcodes.IALOAD:
        case Opcodes.LALOAD:
        case Opcodes.FALOAD:
        case Opcodes.DALOAD:
        case Opcodes.AALOAD:
        case Opcodes.BALOAD:
        case Opcodes.CALOAD:
        case Opcodes.SALOAD:
          instructions.add(new CfArrayLoad(getMemberTypeForOpcode(opcode)));
          break;
        case Opcodes.IASTORE:
        case Opcodes.LASTORE:
        case Opcodes.FASTORE:
        case Opcodes.DASTORE:
        case Opcodes.AASTORE:
        case Opcodes.BASTORE:
        case Opcodes.CASTORE:
        case Opcodes.SASTORE:
          instructions.add(new CfArrayStore(getMemberTypeForOpcode(opcode)));
          break;
        case Opcodes.POP:
        case Opcodes.POP2:
        case Opcodes.DUP:
        case Opcodes.DUP_X1:
        case Opcodes.DUP_X2:
        case Opcodes.DUP2:
        case Opcodes.DUP2_X1:
        case Opcodes.DUP2_X2:
        case Opcodes.SWAP:
          instructions.add(CfStackInstruction.fromAsm(opcode));
          break;
        case Opcodes.IADD:
        case Opcodes.LADD:
        case Opcodes.FADD:
        case Opcodes.DADD:
        case Opcodes.ISUB:
        case Opcodes.LSUB:
        case Opcodes.FSUB:
        case Opcodes.DSUB:
        case Opcodes.IMUL:
        case Opcodes.LMUL:
        case Opcodes.FMUL:
        case Opcodes.DMUL:
        case Opcodes.IDIV:
        case Opcodes.LDIV:
        case Opcodes.FDIV:
        case Opcodes.DDIV:
        case Opcodes.IREM:
        case Opcodes.LREM:
        case Opcodes.FREM:
        case Opcodes.DREM:
          instructions.add(new CfBinop(opcode));
          break;
        case Opcodes.INEG:
        case Opcodes.LNEG:
        case Opcodes.FNEG:
        case Opcodes.DNEG:
          instructions.add(new CfUnop(opcode));
          break;
        case Opcodes.ISHL:
        case Opcodes.LSHL:
        case Opcodes.ISHR:
        case Opcodes.LSHR:
        case Opcodes.IUSHR:
        case Opcodes.LUSHR:
        case Opcodes.IAND:
        case Opcodes.LAND:
        case Opcodes.IOR:
        case Opcodes.LOR:
        case Opcodes.IXOR:
        case Opcodes.LXOR:
          instructions.add(new CfBinop(opcode));
          break;
        case Opcodes.I2L:
        case Opcodes.I2F:
        case Opcodes.I2D:
        case Opcodes.L2I:
        case Opcodes.L2F:
        case Opcodes.L2D:
        case Opcodes.F2I:
        case Opcodes.F2L:
        case Opcodes.F2D:
        case Opcodes.D2I:
        case Opcodes.D2L:
        case Opcodes.D2F:
        case Opcodes.I2B:
        case Opcodes.I2C:
        case Opcodes.I2S:
          instructions.add(new CfUnop(opcode));
          break;
        case Opcodes.LCMP:
        case Opcodes.FCMPL:
        case Opcodes.FCMPG:
        case Opcodes.DCMPL:
        case Opcodes.DCMPG:
          instructions.add(new CfBinop(opcode));
          break;
        case Opcodes.IRETURN:
          instructions.add(new CfReturn(ValueType.INT));
          break;
        case Opcodes.LRETURN:
          instructions.add(new CfReturn(ValueType.LONG));
          break;
        case Opcodes.FRETURN:
          instructions.add(new CfReturn(ValueType.FLOAT));
          break;
        case Opcodes.DRETURN:
          instructions.add(new CfReturn(ValueType.DOUBLE));
          break;
        case Opcodes.ARETURN:
          instructions.add(new CfReturn(ValueType.OBJECT));
          break;
        case Opcodes.RETURN:
          instructions.add(new CfReturnVoid());
          break;
        case Opcodes.ARRAYLENGTH:
          instructions.add(new CfArrayLength());
          break;
        case Opcodes.ATHROW:
          instructions.add(new CfThrow());
          break;
        case Opcodes.MONITORENTER:
          instructions.add(new CfMonitor(Monitor.Type.ENTER));
          break;
        case Opcodes.MONITOREXIT:
          instructions.add(new CfMonitor(Monitor.Type.EXIT));
          break;
        default:
          throw new Unreachable("Unknown instruction");
      }
    }

    private DexType opType(int opcode, DexItemFactory factory) {
      switch (opcode) {
        case Opcodes.IADD:
        case Opcodes.ISUB:
        case Opcodes.IMUL:
        case Opcodes.IDIV:
        case Opcodes.IREM:
        case Opcodes.INEG:
        case Opcodes.ISHL:
        case Opcodes.ISHR:
        case Opcodes.IUSHR:
          return factory.intType;
        case Opcodes.LADD:
        case Opcodes.LSUB:
        case Opcodes.LMUL:
        case Opcodes.LDIV:
        case Opcodes.LREM:
        case Opcodes.LNEG:
        case Opcodes.LSHL:
        case Opcodes.LSHR:
        case Opcodes.LUSHR:
          return factory.longType;
        case Opcodes.FADD:
        case Opcodes.FSUB:
        case Opcodes.FMUL:
        case Opcodes.FDIV:
        case Opcodes.FREM:
        case Opcodes.FNEG:
          return factory.floatType;
        case Opcodes.DADD:
        case Opcodes.DSUB:
        case Opcodes.DMUL:
        case Opcodes.DDIV:
        case Opcodes.DREM:
        case Opcodes.DNEG:
          return factory.doubleType;
        default:
          throw new Unreachable("Unexpected opcode " + opcode);
      }
    }

    private static MemberType getMemberTypeForOpcode(int opcode) {
      switch (opcode) {
        case Opcodes.IALOAD:
        case Opcodes.IASTORE:
          return MemberType.INT;
        case Opcodes.FALOAD:
        case Opcodes.FASTORE:
          return MemberType.FLOAT;
        case Opcodes.LALOAD:
        case Opcodes.LASTORE:
          return MemberType.LONG;
        case Opcodes.DALOAD:
        case Opcodes.DASTORE:
          return MemberType.DOUBLE;
        case Opcodes.AALOAD:
        case Opcodes.AASTORE:
          return MemberType.OBJECT;
        case Opcodes.BALOAD:
        case Opcodes.BASTORE:
          return MemberType.BOOLEAN; // TODO: Distinguish byte and boolean.
        case Opcodes.CALOAD:
        case Opcodes.CASTORE:
          return MemberType.CHAR;
        case Opcodes.SALOAD:
        case Opcodes.SASTORE:
          return MemberType.SHORT;
        default:
          throw new Unreachable("Unexpected array opcode " + opcode);
      }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      switch (opcode) {
        case Opcodes.SIPUSH:
        case Opcodes.BIPUSH:
          instructions.add(new CfConstNumber(operand, ValueType.INT));
          break;
        case Opcodes.NEWARRAY:
          instructions.add(
              new CfNewArray(factory.createArrayType(1, arrayTypeDesc(operand, factory))));
          break;
        default:
          throw new Unreachable("Unexpected int opcode " + opcode);
      }
    }

    private static DexType arrayTypeDesc(int arrayTypeCode, DexItemFactory factory) {
      switch (arrayTypeCode) {
        case Opcodes.T_BOOLEAN:
          return factory.booleanType;
        case Opcodes.T_CHAR:
          return factory.charType;
        case Opcodes.T_FLOAT:
          return factory.floatType;
        case Opcodes.T_DOUBLE:
          return factory.doubleType;
        case Opcodes.T_BYTE:
          return factory.byteType;
        case Opcodes.T_SHORT:
          return factory.shortType;
        case Opcodes.T_INT:
          return factory.intType;
        case Opcodes.T_LONG:
          return factory.longType;
        default:
          throw new Unreachable("Unexpected array-type code " + arrayTypeCode);
      }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      ValueType type;
      switch (opcode) {
        case Opcodes.ILOAD:
        case Opcodes.ISTORE:
          type = ValueType.INT;
          break;
        case Opcodes.FLOAD:
        case Opcodes.FSTORE:
          type = ValueType.FLOAT;
          break;
        case Opcodes.LLOAD:
        case Opcodes.LSTORE:
          type = ValueType.LONG;
          break;
        case Opcodes.DLOAD:
        case Opcodes.DSTORE:
          type = ValueType.DOUBLE;
          break;
        case Opcodes.ALOAD:
        case Opcodes.ASTORE:
          type = ValueType.OBJECT;
          break;
        case Opcodes.RET:
          throw new Unreachable("RET should be handled by the ASM jsr inliner");
        default:
          throw new Unreachable("Unexpected VarInsn opcode: " + opcode);
      }
      if (Opcodes.ILOAD <= opcode && opcode <= Opcodes.ALOAD) {
        instructions.add(new CfLoad(type, var));
      } else {
        instructions.add(new CfStore(type, var));
      }
    }

    @Override
    public void visitTypeInsn(int opcode, String typeName) {
      DexType type = createTypeFromInternalType(typeName);
      switch (opcode) {
        case Opcodes.NEW:
          instructions.add(new CfNew(type));
          break;
        case Opcodes.ANEWARRAY:
          instructions.add(new CfNewArray(factory.createArrayType(1, type)));
          break;
        case Opcodes.CHECKCAST:
          instructions.add(new CfCheckCast(type));
          break;
        case Opcodes.INSTANCEOF:
          instructions.add(new CfInstanceOf(type));
          break;
        default:
          throw new Unreachable("Unexpected TypeInsn opcode: " + opcode);
      }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      DexField field =
          factory.createField(createTypeFromInternalType(owner), factory.createType(desc), name);
      // TODO(mathiasr): Don't require CfFieldInstruction::declaringField. It is needed for proper
      // renaming in the backend, but it is not available here in the frontend.
      instructions.add(new CfFieldInstruction(opcode, field, field));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
      visitMethodInsn(opcode, owner, name, desc, false);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      DexMethod method = application.getMethod(owner, name, desc);
      instructions.add(new CfInvoke(opcode, method, itf));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      DexCallSite callSite =
          DexCallSite.fromAsmInvokeDynamic(application, parent.type, name, desc, bsm, bsmArgs);
      instructions.add(new CfInvokeDynamic(callSite));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      CfLabel target = getLabel(label);
      if (Opcodes.IFEQ <= opcode && opcode <= Opcodes.IF_ACMPNE) {
        if (opcode <= Opcodes.IFLE) {
          // IFEQ, IFNE, IFLT, IFGE, IFGT, or IFLE.
          instructions.add(new CfIf(ifType(opcode), ValueType.INT, target));
        } else {
          // IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, or
          // IF_ACMPNE.
          ValueType valueType;
          if (opcode <= Opcodes.IF_ICMPLE) {
            valueType = ValueType.INT;
          } else {
            valueType = ValueType.OBJECT;
          }
          instructions.add(new CfIfCmp(ifType(opcode), valueType, target));
        }
      } else {
        // GOTO, JSR, IFNULL or IFNONNULL.
        switch (opcode) {
          case Opcodes.GOTO:
            instructions.add(new CfGoto(target));
            break;
          case Opcodes.IFNULL:
          case Opcodes.IFNONNULL:
            If.Type type = opcode == Opcodes.IFNULL ? If.Type.EQ : If.Type.NE;
            instructions.add(new CfIf(type, ValueType.OBJECT, target));
            break;
          case Opcodes.JSR:
            throw new Unreachable("JSR should be handled by the ASM jsr inliner");
          default:
            throw new Unreachable("Unexpected JumpInsn opcode: " + opcode);
        }
      }
    }

    private static If.Type ifType(int opcode) {
      switch (opcode) {
        case Opcodes.IFEQ:
        case Opcodes.IF_ICMPEQ:
        case Opcodes.IF_ACMPEQ:
          return If.Type.EQ;
        case Opcodes.IFNE:
        case Opcodes.IF_ICMPNE:
        case Opcodes.IF_ACMPNE:
          return If.Type.NE;
        case Opcodes.IFLT:
        case Opcodes.IF_ICMPLT:
          return If.Type.LT;
        case Opcodes.IFGE:
        case Opcodes.IF_ICMPGE:
          return If.Type.GE;
        case Opcodes.IFGT:
        case Opcodes.IF_ICMPGT:
          return If.Type.GT;
        case Opcodes.IFLE:
        case Opcodes.IF_ICMPLE:
          return If.Type.LE;
        default:
          throw new Unreachable("Unexpected If instruction opcode: " + opcode);
      }
    }

    @Override
    public void visitLabel(Label label) {
      instructions.add(getLabel(label));
    }

    @Override
    public void visitLdcInsn(Object cst) {
      if (cst instanceof Type) {
        Type type = (Type) cst;
        if (type.getSort() == Type.METHOD) {
          DexProto proto = application.getProto(type.getDescriptor());
          instructions.add(new CfConstMethodType(proto));
        } else {
          instructions.add(new CfConstClass(factory.createType(type.getDescriptor())));
        }
      } else if (cst instanceof String) {
        instructions.add(new CfConstString(factory.createString((String) cst)));
      } else if (cst instanceof Long) {
        instructions.add(new CfConstNumber((Long) cst, ValueType.LONG));
      } else if (cst instanceof Double) {
        long l = Double.doubleToRawLongBits((Double) cst);
        instructions.add(new CfConstNumber(l, ValueType.DOUBLE));
      } else if (cst instanceof Integer) {
        instructions.add(new CfConstNumber((Integer) cst, ValueType.INT));
      } else if (cst instanceof Float) {
        long i = Float.floatToRawIntBits((Float) cst);
        instructions.add(new CfConstNumber(i, ValueType.FLOAT));
      } else if (cst instanceof Handle) {
        instructions.add(
            new CfConstMethodHandle(
                DexMethodHandle.fromAsmHandle((Handle) cst, application, parent.type)));
      } else {
        throw new CompilationError("Unsupported constant: " + cst.toString());
      }
    }

    @Override
    public void visitIincInsn(int var, int increment) {
      instructions.add(new CfIinc(var, increment));
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      assert max == min + labels.length - 1;
      ArrayList<CfLabel> targets = new ArrayList<>(labels.length);
      for (Label label : labels) {
        targets.add(getLabel(label));
      }
      instructions.add(new CfSwitch(CfSwitch.Kind.TABLE, getLabel(dflt), new int[] {min}, targets));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      ArrayList<CfLabel> targets = new ArrayList<>(labels.length);
      for (Label label : labels) {
        targets.add(getLabel(label));
      }
      instructions.add(new CfSwitch(CfSwitch.Kind.LOOKUP, getLabel(dflt), keys, targets));
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
      instructions.add(new CfMultiANewArray(factory.createType(desc), dims));
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      List<DexType> guards =
          Collections.singletonList(
              type == null ? DexItemFactory.catchAllType : createTypeFromInternalType(type));
      List<CfLabel> targets = Collections.singletonList(getLabel(handler));
      tryCatchRanges.add(new CfTryCatch(getLabel(start), getLabel(end), guards, targets));
    }

    @Override
    public void visitLocalVariable(
        String name, String desc, String signature, Label start, Label end, int index) {
      DebugLocalInfo debugLocalInfo =
          new DebugLocalInfo(
              factory.createString(name),
              factory.createType(desc),
              signature == null ? null : factory.createString(signature));
      localVariables.add(
          new LocalVariableInfo(index, debugLocalInfo, getLabel(start), getLabel(end)));
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      instructions.add(new CfPosition(getLabel(start), new Position(line, null, method, null)));
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      assert maxStack >= 0;
      assert maxLocals >= 0;
      this.maxStack = maxStack;
      this.maxLocals = maxLocals;
    }
  }

  private static class CreateAnnotationVisitor extends AnnotationVisitor {

    private final JarApplicationReader application;
    private final BiConsumer<List<DexString>, List<DexValue>> onVisitEnd;
    private List<DexString> names = null;
    private final List<DexValue> values = new ArrayList<>();

    public CreateAnnotationVisitor(
        JarApplicationReader application, BiConsumer<List<DexString>, List<DexValue>> onVisitEnd) {
      super(ASM6);
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
        addElement(name, new DexValueArray(values.toArray(new DexValue[values.size()])));
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
}
