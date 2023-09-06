// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.ir.desugar.records.RecordDesugaring;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaring;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Type;

/**
 * Common structures used while reading in a Java application from jar files.
 *
 * The primary use of this class is to canonicalize dex items during read.
 * The addition of classes to the builder also takes place through this class.
 * It does not currently support multithreaded reading.
 */
public class JarApplicationReader {

  public final InternalOptions options;
  private final ConcurrentHashMap<String, Type> asmObjectTypeCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Type> asmTypeCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, DexString> stringCache = new ConcurrentHashMap<>();
  private final ApplicationReaderMap applicationReaderMap;
  private final DexApplicationReadFlags.Builder readFlagsBuilder;

  public JarApplicationReader(
      InternalOptions options, DexApplicationReadFlags.Builder readFlagsBuilder) {
    this.options = options;
    this.readFlagsBuilder = readFlagsBuilder;
    applicationReaderMap = ApplicationReaderMap.getInstance(options);
  }

  public JarApplicationReader(InternalOptions options) {
    this(options, DexApplicationReadFlags.builder());
  }

  public Type getAsmObjectType(String name) {
    return asmObjectTypeCache.computeIfAbsent(name, Type::getObjectType);
  }

  public Type getAsmType(String name) {
    return asmTypeCache.computeIfAbsent(name, Type::getType);
  }

  public DexItemFactory getFactory() {
    return options.itemFactory;
  }

  public DexString getString(String string) {
    return stringCache.computeIfAbsent(string, options.itemFactory::createString);
  }

  public DexType getType(Type type) {
    return getTypeFromDescriptor(type.getDescriptor());
  }

  public DexType getTypeFromName(String name) {
    assert isValidInternalName(name);
    return getType(getAsmObjectType(name));
  }

  public DexType getTypeFromDescriptor(String desc) {
    assert isValidDescriptor(desc);
    String actualDesc = applicationReaderMap.getDescriptor(desc);
    return options.itemFactory.createType(getString(actualDesc));
  }

  public DexTypeList getTypeListFromNames(String[] names) {
    if (names.length == 0) {
      return DexTypeList.empty();
    }
    DexType[] types = new DexType[names.length];
    for (int i = 0; i < names.length; i++) {
      types[i] = getTypeFromName(names[i]);
    }
    return new DexTypeList(types);
  }

  public DexTypeList getTypeListFromDescriptors(String[] descriptors) {
    if (descriptors.length == 0) {
      return DexTypeList.empty();
    }
    DexType[] types = new DexType[descriptors.length];
    for (int i = 0; i < descriptors.length; i++) {
      types[i] = getTypeFromDescriptor(descriptors[i]);
    }
    return new DexTypeList(types);
  }

  public DexField getField(String owner, String name, String desc) {
    return getField(getTypeFromName(owner), name, desc);
  }

  public DexField getField(DexType owner, String name, String desc) {
    return options.itemFactory.createField(owner, getTypeFromDescriptor(desc), getString(name));
  }

  public DexMethod getMethod(String owner, String name, String desc) {
    return getMethod(getTypeFromName(owner), name, desc);
  }

  public DexMethod getMethod(DexType owner, String name, String desc) {
    return options.itemFactory.createMethod(owner, getProto(desc), getString(name));
  }

  public DexCallSite getCallSite(String methodName, String methodProto,
      DexMethodHandle bootstrapMethod, List<DexValue> bootstrapArgs) {
    return options.itemFactory.createCallSite(
        getString(methodName), getProto(methodProto), bootstrapMethod, bootstrapArgs);
  }

  public DexMethodHandle getMethodHandle(
      MethodHandleType type,
      DexMember<? extends DexItem, ? extends DexMember<?, ?>> fieldOrMethod,
      boolean isInterface) {
    return options.itemFactory.createMethodHandle(type, fieldOrMethod, isInterface);
  }

  public DexProto getProto(String desc) {
    assert isValidDescriptor(desc);
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(desc);
    String[] argumentDescriptors = DescriptorUtils.getArgumentTypeDescriptors(desc);
    return options.itemFactory.createProto(
        getTypeFromDescriptor(returnTypeDescriptor),
        getTypeListFromDescriptors(argumentDescriptors));
  }

  private boolean isValidDescriptor(String desc) {
    return getAsmType(desc).getDescriptor().equals(desc);
  }

  private boolean isValidInternalName(String name) {
    return getAsmObjectType(name).getInternalName().equals(name);
  }

  public Type getReturnType(final String methodDescriptor) {
    return getAsmType(DescriptorUtils.getReturnTypeDescriptor(methodDescriptor));
  }

  public void addRecordWitness(DexType witness, ClassKind<?> classKind) {
    if (classKind == ClassKind.PROGRAM) {
      readFlagsBuilder.addRecordWitness(witness);
    }
  }

  public void checkFieldForRecord(DexField dexField, ClassKind<?> classKind) {
    if (options.shouldDesugarRecords() && RecordDesugaring.refersToRecord(dexField, getFactory())) {
      addRecordWitness(dexField.getHolderType(), classKind);
    }
  }

  public void checkMethodForRecord(DexMethod dexMethod, ClassKind<?> classKind) {
    if (options.shouldDesugarRecords()
        && RecordDesugaring.refersToRecord(dexMethod, getFactory())) {
      addRecordWitness(dexMethod.getHolderType(), classKind);
    }
  }

  public void addVarHandleWitness(DexType witness, ClassKind<?> classKind) {
    if (classKind == ClassKind.PROGRAM) {
      readFlagsBuilder.addVarHandleWitness(witness);
    }
  }

  public void checkFieldForVarHandle(DexField dexField, ClassKind<?> classKind) {
    if (options.shouldDesugarVarHandle()
        && VarHandleDesugaring.refersToVarHandle(dexField, getFactory())) {
      addVarHandleWitness(dexField.getHolderType(), classKind);
    }
  }

  public void checkMethodForVarHandle(DexMethod dexMethod, ClassKind<?> classKind) {
    if (options.shouldDesugarVarHandle()
        && VarHandleDesugaring.refersToVarHandle(dexMethod, getFactory())) {
      addVarHandleWitness(dexMethod.getHolderType(), classKind);
    }
  }

  public void addMethodHandlesLookupWitness(DexType witness, ClassKind<?> classKind) {
    if (classKind == ClassKind.PROGRAM) {
      readFlagsBuilder.addMethodHandlesLookupWitness(witness);
    }
  }

  public void checkClassForMethodHandlesLookup(DexClass dexClass, ClassKind<?> classKind) {
    if (options.shouldDesugarVarHandle()) {
      if (VarHandleDesugaring.refersToMethodHandlesLookup(dexClass.getType(), getFactory())) {
        addVarHandleWitness(dexClass.getType(), classKind);
      }
      dexClass
          .getInnerClasses()
          .forEach(
              attribute -> {
                // MethodHandles$Lookup has no inner classes.
                assert !VarHandleDesugaring.refersToMethodHandlesLookup(
                    attribute.getOuter(), getFactory());
                if (VarHandleDesugaring.refersToMethodHandlesLookup(
                    attribute.getInner(), getFactory())) {
                  addMethodHandlesLookupWitness(dexClass.getType(), classKind);
                  // When the inner MethodHandles$Lookup is present the outer is MethodHandles, and
                  // in that case the enqueuer will process all methods in the library class
                  // MethodHandles which have references to VarHandle.
                  assert attribute.getOuter() == getFactory().methodHandlesType;
                  addVarHandleWitness(dexClass.getType(), classKind);
                }
              });
    }
  }

  public void checkFieldForMethodHandlesLookup(DexField dexField, ClassKind<?> classKind) {
    if (options.shouldDesugarVarHandle()
        && VarHandleDesugaring.refersToMethodHandlesLookup(dexField, getFactory())) {
      addMethodHandlesLookupWitness(dexField.getHolderType(), classKind);
    }
  }

  public void checkMethodForMethodHandlesLookup(DexMethod dexMethod, ClassKind<?> classKind) {
    if (options.shouldDesugarVarHandle()
        && VarHandleDesugaring.refersToMethodHandlesLookup(dexMethod, getFactory())) {
      addMethodHandlesLookupWitness(dexMethod.getHolderType(), classKind);
    }
  }
}
