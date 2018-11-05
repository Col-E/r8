// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.DexDebugEvent.AdvanceLine;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetInlineFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.ir.analysis.type.ArrayTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeLatticeElement;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.ArrayUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexItemFactory {

  private final ConcurrentHashMap<DexString, DexString> strings = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexString, DexType> types = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexField, DexField> fields = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexProto, DexProto> protos = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexMethod, DexMethod> methods = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexMethodHandle, DexMethodHandle> methodHandles =
      new ConcurrentHashMap<>();

  // DexDebugEvent Canonicalization.
  private final Int2ObjectMap<AdvanceLine> advanceLines = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<AdvancePC> advancePCs = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<Default> defaults = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<EndLocal> endLocals = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<RestartLocal> restartLocals = new Int2ObjectOpenHashMap<>();
  private final SetEpilogueBegin setEpilogueBegin = new SetEpilogueBegin();
  private final SetPrologueEnd setPrologueEnd = new SetPrologueEnd();
  private final Map<DexString, SetFile> setFiles = new HashMap<>();
  private final Map<SetInlineFrame, SetInlineFrame> setInlineFrames = new HashMap<>();

  // ReferenceTypeLattice canonicalization.
  private final ConcurrentHashMap<DexType, ReferenceTypeLatticeElement>
      referenceTypeLatticeElements = new ConcurrentHashMap<>();

  boolean sorted = false;

  public static final DexType catchAllType = new DexType(new DexString("CATCH_ALL"));

  // Internal type containing only the null value.
  public static final DexType nullValueType = new DexType(new DexString("NULL"));

  public static final DexString unknownTypeName = new DexString("UNKNOWN");
  public static final DexType unknownType = new DexType(unknownTypeName);

  private static final IdentityHashMap<DexItem, DexItem> internalSentinels =
      new IdentityHashMap<>(
          ImmutableMap.of(
              catchAllType, catchAllType,
              nullValueType, nullValueType,
              unknownType, unknownType,
              unknownTypeName, unknownTypeName));

  public DexItemFactory() {
    this.kotlin = new Kotlin(this);
  }

  public static boolean isInternalSentinel(DexItem item) {
    return internalSentinels.containsKey(item);
  }

  public final DexString booleanDescriptor = createString("Z");
  public final DexString byteDescriptor = createString("B");
  public final DexString charDescriptor = createString("C");
  public final DexString doubleDescriptor = createString("D");
  public final DexString floatDescriptor = createString("F");
  public final DexString intDescriptor = createString("I");
  public final DexString longDescriptor = createString("J");
  public final DexString shortDescriptor = createString("S");
  public final DexString voidDescriptor = createString("V");

  public final DexString boxedBooleanDescriptor = createString("Ljava/lang/Boolean;");
  public final DexString boxedByteDescriptor = createString("Ljava/lang/Byte;");
  public final DexString boxedCharDescriptor = createString("Ljava/lang/Character;");
  public final DexString boxedDoubleDescriptor = createString("Ljava/lang/Double;");
  public final DexString boxedFloatDescriptor = createString("Ljava/lang/Float;");
  public final DexString boxedIntDescriptor = createString("Ljava/lang/Integer;");
  public final DexString boxedLongDescriptor = createString("Ljava/lang/Long;");
  public final DexString boxedShortDescriptor = createString("Ljava/lang/Short;");
  public final DexString boxedNumberDescriptor = createString("Ljava/lang/Number;");

  public final DexString unboxBooleanMethodName = createString("booleanValue");
  public final DexString unboxByteMethodName = createString("byteValue");
  public final DexString unboxCharMethodName = createString("charValue");
  public final DexString unboxShortMethodName = createString("shortValue");
  public final DexString unboxIntMethodName = createString("intValue");
  public final DexString unboxLongMethodName = createString("longValue");
  public final DexString unboxFloatMethodName = createString("floatValue");
  public final DexString unboxDoubleMethodName = createString("doubleValue");

  public final DexString lengthMethodName = createString("length");
  public final DexString valueOfMethodName = createString("valueOf");

  public final DexString getClassMethodName = createString("getClass");
  public final DexString finalizeMethodName = createString("finalize");
  public final DexString ordinalMethodName = createString("ordinal");
  public final DexString desiredAssertionStatusMethodName = createString("desiredAssertionStatus");
  public final DexString forNameMethodName = createString("forName");
  public final DexString getNameName = createString("getName");
  public final DexString getSimpleNameName = createString("getSimpleName");
  public final DexString getFieldName = createString("getField");
  public final DexString getDeclaredFieldName = createString("getDeclaredField");
  public final DexString getMethodName = createString("getMethod");
  public final DexString getDeclaredMethodName = createString("getDeclaredMethod");
  public final DexString assertionsDisabled = createString("$assertionsDisabled");
  public final DexString invokeMethodName = createString("invoke");
  public final DexString invokeExactMethodName = createString("invokeExact");

  public final DexString stringDescriptor = createString("Ljava/lang/String;");
  public final DexString stringArrayDescriptor = createString("[Ljava/lang/String;");
  public final DexString objectDescriptor = createString("Ljava/lang/Object;");
  public final DexString objectArrayDescriptor = createString("[Ljava/lang/Object;");
  public final DexString classDescriptor = createString("Ljava/lang/Class;");
  public final DexString autoCloseableDescriptor = createString("Ljava/lang/AutoCloseable;");
  public final DexString classArrayDescriptor = createString("[Ljava/lang/Class;");
  public final DexString fieldDescriptor = createString("Ljava/lang/reflect/Field;");
  public final DexString methodDescriptor = createString("Ljava/lang/reflect/Method;");
  public final DexString enumDescriptor = createString("Ljava/lang/Enum;");
  public final DexString annotationDescriptor = createString("Ljava/lang/annotation/Annotation;");
  public final DexString throwableDescriptor = createString("Ljava/lang/Throwable;");
  public final DexString objectsDescriptor = createString("Ljava/util/Objects;");
  public final DexString stringBuilderDescriptor = createString("Ljava/lang/StringBuilder;");
  public final DexString stringBufferDescriptor = createString("Ljava/lang/StringBuffer;");
  public final DexString varHandleDescriptor = createString("Ljava/lang/invoke/VarHandle;");
  public final DexString methodHandleDescriptor = createString("Ljava/lang/invoke/MethodHandle;");
  public final DexString methodTypeDescriptor = createString("Ljava/lang/invoke/MethodType;");

  public final DexString npeDescriptor = createString("Ljava/lang/NullPointerException;");

  public final DexString intFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;");
  public final DexString longFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicLongFieldUpdater;");
  public final DexString referenceFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;");
  public final DexString newUpdaterName = createString("newUpdater");

  public final DexString constructorMethodName = createString(Constants.INSTANCE_INITIALIZER_NAME);
  public final DexString classConstructorMethodName = createString(Constants.CLASS_INITIALIZER_NAME);

  public final DexString thisName = createString("this");

  private final DexString charArrayDescriptor = createString("[C");
  private final DexType charArrayType = createType(charArrayDescriptor);
  public final DexString throwableArrayDescriptor = createString("[Ljava/lang/Throwable;");

  public final DexType booleanType = createType(booleanDescriptor);
  public final DexType byteType = createType(byteDescriptor);
  public final DexType charType = createType(charDescriptor);
  public final DexType doubleType = createType(doubleDescriptor);
  public final DexType floatType = createType(floatDescriptor);
  public final DexType intType = createType(intDescriptor);
  public final DexType longType = createType(longDescriptor);
  public final DexType shortType = createType(shortDescriptor);
  public final DexType voidType = createType(voidDescriptor);

  public final DexType boxedBooleanType = createType(boxedBooleanDescriptor);
  public final DexType boxedByteType = createType(boxedByteDescriptor);
  public final DexType boxedCharType = createType(boxedCharDescriptor);
  public final DexType boxedDoubleType = createType(boxedDoubleDescriptor);
  public final DexType boxedFloatType = createType(boxedFloatDescriptor);
  public final DexType boxedIntType = createType(boxedIntDescriptor);
  public final DexType boxedLongType = createType(boxedLongDescriptor);
  public final DexType boxedShortType = createType(boxedShortDescriptor);
  public final DexType boxedNumberType = createType(boxedNumberDescriptor);

  public final DexType stringType = createType(stringDescriptor);
  public final DexType stringArrayType = createType(stringArrayDescriptor);
  public final DexType objectType = createType(objectDescriptor);
  public final DexType objectArrayType = createType(objectArrayDescriptor);
  public final DexType classArrayType = createType(classArrayDescriptor);
  public final DexType enumType = createType(enumDescriptor);
  public final DexType annotationType = createType(annotationDescriptor);
  public final DexType throwableType = createType(throwableDescriptor);
  public final DexType classType = createType(classDescriptor);
  public final DexType autoCloseableType = createType(autoCloseableDescriptor);

  public final DexType stringBuilderType = createType(stringBuilderDescriptor);
  public final DexType stringBufferType = createType(stringBufferDescriptor);

  public final DexType varHandleType = createType(varHandleDescriptor);
  public final DexType methodHandleType = createType(methodHandleDescriptor);
  public final DexType methodTypeType = createType(methodTypeDescriptor);

  public final DexType npeType = createType(npeDescriptor);

  public final StringBuildingMethods stringBuilderMethods =
      new StringBuildingMethods(stringBuilderType);
  public final StringBuildingMethods stringBufferMethods =
      new StringBuildingMethods(stringBufferType);
  public final ObjectsMethods objectsMethods = new ObjectsMethods();
  public final ObjectMethods objectMethods = new ObjectMethods();
  public final StringMethods stringMethods = new StringMethods();
  public final LongMethods longMethods = new LongMethods();
  public final ThrowableMethods throwableMethods = new ThrowableMethods();
  public final ClassMethods classMethods = new ClassMethods();
  public final EnumMethods enumMethods = new EnumMethods();
  public final PrimitiveTypesBoxedTypeFields primitiveTypesBoxedTypeFields =
      new PrimitiveTypesBoxedTypeFields();
  public final AtomicFieldUpdaterMethods atomicFieldUpdaterMethods =
      new AtomicFieldUpdaterMethods();
  public final Kotlin kotlin;
  public final PolymorphicMethods polymorphicMethods = new PolymorphicMethods();

  public final DexString twrCloseResourceMethodName = createString("$closeResource");
  public final DexProto twrCloseResourceMethodProto =
      createProto(voidType, throwableType, autoCloseableType);

  // Dex system annotations.
  // See https://source.android.com/devices/tech/dalvik/dex-format.html#system-annotation
  public final DexType annotationDefault = createType("Ldalvik/annotation/AnnotationDefault;");
  public final DexType annotationEnclosingClass = createType("Ldalvik/annotation/EnclosingClass;");
  public final DexType annotationEnclosingMethod = createType(
      "Ldalvik/annotation/EnclosingMethod;");
  public final DexType annotationInnerClass = createType("Ldalvik/annotation/InnerClass;");
  public final DexType annotationMemberClasses = createType("Ldalvik/annotation/MemberClasses;");
  public final DexType annotationMethodParameters = createType(
      "Ldalvik/annotation/MethodParameters;");
  public final DexType annotationSignature = createType("Ldalvik/annotation/Signature;");
  public final DexType annotationSourceDebugExtension = createType(
      "Ldalvik/annotation/SourceDebugExtension;");
  public final DexType annotationThrows = createType("Ldalvik/annotation/Throws;");
  public final DexType annotationSynthesizedClassMap =
      createType("Lcom/android/tools/r8/annotations/SynthesizedClassMap;");
  public final DexType annotationCovariantReturnType =
      createType("Ldalvik/annotation/codegen/CovariantReturnType;");
  public final DexType annotationCovariantReturnTypes =
      createType("Ldalvik/annotation/codegen/CovariantReturnType$CovariantReturnTypes;");

  private static final String METAFACTORY_METHOD_NAME = "metafactory";
  private static final String METAFACTORY_ALT_METHOD_NAME = "altMetafactory";

  public final DexType metafactoryType = createType("Ljava/lang/invoke/LambdaMetafactory;");
  public final DexType callSiteType = createType("Ljava/lang/invoke/CallSite;");
  public final DexType lookupType = createType("Ljava/lang/invoke/MethodHandles$Lookup;");
  public final DexType serializableType = createType("Ljava/io/Serializable;");
  public final DexType externalizableType = createType("Ljava/io/Externalizable;");
  public final DexType comparableType = createType("Ljava/lang/Comparable;");

  public final DexMethod metafactoryMethod =
      createMethod(
          metafactoryType,
          createProto(
              callSiteType,
              lookupType,
              stringType,
              methodTypeType,
              methodTypeType,
              methodHandleType,
              methodTypeType),
          createString(METAFACTORY_METHOD_NAME));

  public final DexMethod metafactoryAltMethod =
      createMethod(
          metafactoryType,
          createProto(callSiteType, lookupType, stringType, methodTypeType, objectArrayType),
          createString(METAFACTORY_ALT_METHOD_NAME));

  public final DexType stringConcatFactoryType =
      createType("Ljava/lang/invoke/StringConcatFactory;");

  public final DexMethod stringConcatWithConstantsMethod =
      createMethod(
          stringConcatFactoryType,
          createProto(
              callSiteType,
              lookupType,
              stringType,
              methodTypeType,
              stringType,
              objectArrayType),
          createString("makeConcatWithConstants")
      );

  public final DexMethod stringConcatMethod =
      createMethod(
          stringConcatFactoryType,
          createProto(
              callSiteType,
              lookupType,
              stringType,
              methodTypeType),
          createString("makeConcat")
      );

  private boolean skipNameValidationForTesting = false;

  public void setSkipNameValidationForTesting(boolean skipNameValidationForTesting) {
    this.skipNameValidationForTesting = skipNameValidationForTesting;
  }

  public boolean getSkipNameValidationForTesting() {
    return skipNameValidationForTesting;
  }

  public boolean isLambdaMetafactoryMethod(DexMethod dexMethod) {
    return dexMethod == metafactoryMethod || dexMethod == metafactoryAltMethod;
  }

  public synchronized void clearSubtypeInformation() {
    types.values().forEach(DexType::clearSubtypeInformation);
  }

  public class LongMethods {

    public final DexMethod compare;

    private LongMethods() {
      compare = createMethod(boxedLongDescriptor,
          createString("compare"), intDescriptor, new DexString[]{longDescriptor, longDescriptor});
    }
  }

  public class ThrowableMethods {

    public final DexMethod addSuppressed;
    public final DexMethod getSuppressed;

    private ThrowableMethods() {
      addSuppressed = createMethod(throwableDescriptor,
          createString("addSuppressed"), voidDescriptor, new DexString[]{throwableDescriptor});
      getSuppressed = createMethod(throwableDescriptor,
          createString("getSuppressed"), throwableArrayDescriptor, DexString.EMPTY_ARRAY);
    }
  }

  public class ObjectMethods {

    public final DexMethod getClass;
    public final DexMethod constructor;
    public final DexMethod finalize;

    private ObjectMethods() {
      getClass = createMethod(objectDescriptor, getClassMethodName, classDescriptor,
          DexString.EMPTY_ARRAY);
      constructor = createMethod(objectDescriptor,
          constructorMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
      finalize = createMethod(objectDescriptor,
          finalizeMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
    }
  }

  public class ObjectsMethods {

    public DexMethod requireNonNull;

    private ObjectsMethods() {
      requireNonNull = createMethod(objectsDescriptor,
          createString("requireNonNull"), objectDescriptor, new DexString[]{objectDescriptor});
    }
  }

  public class ClassMethods {

    public final DexMethod desiredAssertionStatus;
    public final DexMethod forName;
    public final DexMethod getName;
    public final DexMethod getSimpleName;
    public final DexMethod getField;
    public final DexMethod getDeclaredField;
    public final DexMethod getMethod;
    public final DexMethod getDeclaredMethod;
    private final Set<DexMethod> getMembers;

    private ClassMethods() {
      desiredAssertionStatus = createMethod(classDescriptor,
          desiredAssertionStatusMethodName, booleanDescriptor, DexString.EMPTY_ARRAY);
      forName = createMethod(classDescriptor,
          forNameMethodName, classDescriptor, new DexString[]{stringDescriptor});
      getName = createMethod(classDescriptor, getNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getSimpleName = createMethod(classDescriptor,
          getSimpleNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getField = createMethod(classDescriptor, getFieldName, fieldDescriptor,
          new DexString[]{stringDescriptor});
      getDeclaredField = createMethod(classDescriptor, getDeclaredFieldName, fieldDescriptor,
          new DexString[]{stringDescriptor});
      getMethod = createMethod(classDescriptor, getMethodName, methodDescriptor,
          new DexString[]{stringDescriptor, classArrayDescriptor});
      getDeclaredMethod = createMethod(classDescriptor, getDeclaredMethodName, methodDescriptor,
          new DexString[]{stringDescriptor, classArrayDescriptor});
      getMembers = ImmutableSet.of(getField, getDeclaredField, getMethod, getDeclaredMethod);
    }

    public boolean isReflectiveMemberLookup(DexMethod method) {
      return getMembers.contains(method);
    }
  }

  public class EnumMethods {

    public DexMethod valueOf;

    private EnumMethods() {
      valueOf =
          createMethod(
              enumDescriptor,
              valueOfMethodName,
              enumDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
    }
  }

  /**
   * All boxed types (Boolean, Byte, ...) have a field named TYPE which contains the Class object
   * for the primitive type.
   *
   * E.g. for Boolean https://docs.oracle.com/javase/8/docs/api/java/lang/Boolean.html#TYPE.
   */
  public class PrimitiveTypesBoxedTypeFields {
    public final DexField booleanTYPE;
    public final DexField byteTYPE;
    public final DexField charTYPE;
    public final DexField shortTYPE;
    public final DexField intTYPE;
    public final DexField longTYPE;
    public final DexField floatTYPE;
    public final DexField doubleTYPE;

    private final Map<DexField, DexType> boxedFieldTypeToPrimitiveType;

    private PrimitiveTypesBoxedTypeFields() {
      booleanTYPE = createField(boxedBooleanType, classType, "TYPE");
      byteTYPE = createField(boxedByteType, classType, "TYPE");
      charTYPE = createField(boxedCharType, classType, "TYPE");
      shortTYPE = createField(boxedShortType, classType, "TYPE");
      intTYPE = createField(boxedIntType, classType, "TYPE");
      longTYPE = createField(boxedLongType, classType, "TYPE");
      floatTYPE = createField(boxedFloatType, classType, "TYPE");
      doubleTYPE = createField(boxedDoubleType, classType, "TYPE");

      boxedFieldTypeToPrimitiveType =
          ImmutableMap.<DexField, DexType>builder()
              .put(booleanTYPE, booleanType)
              .put(byteTYPE, byteType)
              .put(charTYPE, charType)
              .put(shortTYPE, shortType)
              .put(intTYPE, intType)
              .put(longTYPE, longType)
              .put(floatTYPE, floatType)
              .put(doubleTYPE, doubleType)
              .build();
    }

    public DexType boxedFieldTypeToPrimitiveType(DexField field) {
      return boxedFieldTypeToPrimitiveType.get(field);
    }
  }

  /**
   * A class that encompasses methods that create different types of atomic field updaters:
   * Atomic(Integer|Long|Reference)FieldUpdater#newUpdater.
   */
  public class AtomicFieldUpdaterMethods {
    public final DexMethod intUpdater;
    public final DexMethod longUpdater;
    public final DexMethod referenceUpdater;
    private final Set<DexMethod> updaters;

    private AtomicFieldUpdaterMethods() {
      intUpdater =
          createMethod(
              intFieldUpdaterDescriptor,
              newUpdaterName,
              intFieldUpdaterDescriptor,
              new DexString[]{classDescriptor, stringDescriptor});
      longUpdater =
          createMethod(
              longFieldUpdaterDescriptor,
              newUpdaterName,
              longFieldUpdaterDescriptor,
              new DexString[]{classDescriptor, stringDescriptor});
      referenceUpdater =
          createMethod(
              referenceFieldUpdaterDescriptor,
              newUpdaterName,
              referenceFieldUpdaterDescriptor,
              new DexString[]{classDescriptor, classDescriptor, stringDescriptor});
      updaters = ImmutableSet.of(intUpdater, longUpdater, referenceUpdater);
    }

    public boolean isFieldUpdater(DexMethod method) {
      return updaters.contains(method);
    }
  }

  public class StringMethods {
    public final DexMethod length;
    public final DexMethod valueOf;

    private StringMethods() {
      length = createMethod(
          stringDescriptor, lengthMethodName, intDescriptor, DexString.EMPTY_ARRAY);
      valueOf = createMethod(
          stringDescriptor, valueOfMethodName, stringDescriptor, new DexString[]{objectDescriptor});
    }
  }

  public class StringBuildingMethods {

    public final DexMethod appendBoolean;
    public final DexMethod appendChar;
    public final DexMethod appendCharArray;
    public final DexMethod appendSubCharArray;
    public final DexMethod appendCharSequence;
    public final DexMethod appendSubCharSequence;
    public final DexMethod appendInt;
    public final DexMethod appendDouble;
    public final DexMethod appendFloat;
    public final DexMethod appendLong;
    public final DexMethod appendObject;
    public final DexMethod appendString;
    public final DexMethod appendStringBuffer;

    private StringBuildingMethods(DexType receiver) {
      DexType sbufType = createType(createString("Ljava/lang/StringBuffer;"));
      DexType charSequenceType = createType(createString("Ljava/lang/CharSequence;"));
      DexString append = createString("append");
      DexString toStringMethodName = createString("toString");

      appendBoolean = createMethod(receiver, createProto(receiver, booleanType), append);
      appendChar = createMethod(receiver, createProto(receiver, charType), append);
      appendCharArray = createMethod(receiver, createProto(receiver, charArrayType), append);
      appendSubCharArray =
          createMethod(receiver, createProto(receiver, charArrayType, intType, intType), append);
      appendCharSequence = createMethod(receiver, createProto(receiver, charSequenceType), append);
      appendSubCharSequence =
          createMethod(receiver, createProto(receiver, charSequenceType, intType, intType), append);
      appendInt = createMethod(receiver, createProto(receiver, intType), append);
      appendDouble = createMethod(receiver, createProto(receiver, doubleType), append);
      appendFloat = createMethod(receiver, createProto(receiver, floatType), append);
      appendLong = createMethod(receiver, createProto(receiver, longType), append);
      appendObject = createMethod(receiver, createProto(receiver, objectType), append);
      appendString = createMethod(receiver, createProto(receiver, stringType), append);
      appendStringBuffer = createMethod(receiver, createProto(receiver, sbufType), append);
    }

    public void forEachAppendMethod(Consumer<DexMethod> consumer) {
      consumer.accept(appendBoolean);
      consumer.accept(appendChar);
      consumer.accept(appendCharArray);
      consumer.accept(appendSubCharArray);
      consumer.accept(appendCharSequence);
      consumer.accept(appendSubCharSequence);
      consumer.accept(appendInt);
      consumer.accept(appendDouble);
      consumer.accept(appendFloat);
      consumer.accept(appendLong);
      consumer.accept(appendObject);
      consumer.accept(appendString);
      consumer.accept(appendStringBuffer);
      consumer.accept(appendBoolean);
    }
  }

  public class PolymorphicMethods {

    private final DexProto signature = createProto(objectType, objectArrayType);
    private final DexProto setSignature = createProto(voidType, objectArrayType);
    private final DexProto compareAndSetSignature = createProto(booleanType, objectArrayType);

    private final Set<DexString> varHandleMethods =
        createStrings(
            "compareAndExchange",
            "compareAndExchangeAcquire",
            "compareAndExchangeRelease",
            "get",
            "getAcquire",
            "getAndAdd",
            "getAndAddAcquire",
            "getAndAddRelease",
            "getAndBitwiseAnd",
            "getAndBitwiseAndAcquire",
            "getAndBitwiseAndRelease",
            "getAndBitwiseOr",
            "getAndBitwiseOrAcquire",
            "getAndBitwiseOrRelease",
            "getAndBitwiseXor",
            "getAndBitwiseXorAcquire",
            "getAndBitwiseXorRelease",
            "getAndSet",
            "getAndSetAcquire",
            "getAndSetRelease",
            "getOpaque",
            "getVolatile");

    private final Set<DexString> varHandleSetMethods =
        createStrings("set", "setOpaque", "setRelease", "setVolatile");

    private final Set<DexString> varHandleCompareAndSetMethods =
        createStrings(
            "compareAndSet",
            "weakCompareAndSet",
            "weakCompareAndSetAcquire",
            "weakCompareAndSetPlain",
            "weakCompareAndSetRelease");

    public DexMethod canonicalize(DexMethod invokeProto) {
      if (invokeProto.holder == methodHandleType) {
        if (invokeProto.name == invokeMethodName || invokeProto.name == invokeExactMethodName) {
          return createMethod(methodHandleType, signature, invokeProto.name);
        }
      } else if (invokeProto.holder == varHandleType) {
        if (varHandleMethods.contains(invokeProto.name)) {
          return createMethod(varHandleType, signature, invokeProto.name);
        } else if (varHandleSetMethods.contains(invokeProto.name)) {
          return createMethod(varHandleType, setSignature, invokeProto.name);
        } else if (varHandleCompareAndSetMethods.contains(invokeProto.name)) {
          return createMethod(varHandleType, compareAndSetSignature, invokeProto.name);
        }
      }
      return null;
    }

    private Set<DexString> createStrings(String... strings) {
      IdentityHashMap<DexString, DexString> map = new IdentityHashMap<>();
      for (String string : strings) {
        DexString dexString = createString(string);
        map.put(dexString, dexString);
      }
      return map.keySet();
    }
  }

  private static <T extends DexItem> T canonicalize(ConcurrentHashMap<T, T> map, T item) {
    assert item != null;
    assert !DexItemFactory.isInternalSentinel(item);
    T previous = map.putIfAbsent(item, item);
    return previous == null ? item : previous;
  }

  public DexString createString(int size, byte[] content) {
    assert !sorted;
    return canonicalize(strings, new DexString(size, content));
  }

  public DexString createString(String source) {
    assert !sorted;
    return canonicalize(strings, new DexString(source));
  }

  public DexString lookupString(String source) {
    return strings.get(new DexString(source));
  }

  // Debugging support to extract marking string.
  public synchronized Collection<Marker> extractMarker() {
    // This is slow but it is not needed for any production code yet.
    List<Marker> markers = new ArrayList<>();
    for (DexString dexString : strings.keySet()) {
      Marker result = Marker.parse(dexString);
      if (result != null) {
        markers.add(result);
      }
    }
    return markers;
  }

  // Debugging support to extract marking string.
  // Find all markers.
  public synchronized List<Marker> extractMarkers() {
    // This is slow but it is not needed for any production code yet.
    List<Marker> markers = new ArrayList<>();
    for (DexString dexString : strings.keySet()) {
      Marker marker = Marker.parse(dexString);
      if (marker != null) {
        markers.add(marker);
      }
    }
    return markers;
  }

  synchronized public DexType createType(DexString descriptor) {
    assert !sorted;
    assert descriptor != null;
    DexType result = types.get(descriptor);
    if (result == null) {
      result = new DexType(descriptor);
      assert result.isArrayType() || result.isClassType() || result.isPrimitiveType() ||
          result.isVoidType();
      assert !isInternalSentinel(result);
      types.put(descriptor, result);
    }
    return result;
  }

  public DexType createType(String descriptor) {
    return createType(createString(descriptor));
  }

  public DexType lookupType(DexString descriptor) {
    return types.get(descriptor);
  }

  public DexType createArrayType(int nesting, DexType baseType) {
    assert nesting > 0;
    return createType(Strings.repeat("[", nesting) + baseType.toDescriptorString());
  }

  public DexField createField(DexType clazz, DexType type, DexString name) {
    assert !sorted;
    DexField field = new DexField(clazz, type, name, skipNameValidationForTesting);
    return canonicalize(fields, field);
  }

  public DexField createField(DexType clazz, DexType type, String name) {
    return createField(clazz, type, createString(name));
  }

  public DexProto createProto(DexType returnType, DexString shorty, DexTypeList parameters) {
    assert !sorted;
    DexProto proto = new DexProto(shorty, returnType, parameters);
    return canonicalize(protos, proto);
  }

  public DexProto createProto(DexType returnType, DexType... parameters) {
    assert !sorted;
    return createProto(returnType, createShorty(returnType, parameters),
        parameters.length == 0 ? DexTypeList.empty() : new DexTypeList(parameters));
  }

  public DexProto applyClassMappingToProto(
      DexProto proto, Function<DexType, DexType> mapping, Map<DexProto, DexProto> cache) {
    assert cache != null;
    DexProto result = cache.get(proto);
    if (result == null) {
      DexType returnType = mapping.apply(proto.returnType);
      DexType[] parameters = applyClassMappingToDexTypes(proto.parameters.values, mapping);
      if (returnType == proto.returnType && parameters == proto.parameters.values) {
        result = proto;
      } else {
        // Should be different if reference has changed.
        assert returnType == proto.returnType || !returnType.equals(proto.returnType);
        assert parameters == proto.parameters.values
            || !Arrays.equals(parameters, proto.parameters.values);
        result = createProto(returnType, parameters);
      }
      cache.put(proto, result);
    }
    return result;
  }

  private static DexType[] applyClassMappingToDexTypes(
      DexType[] types, Function<DexType, DexType> mapping) {
    Map<Integer, DexType> changed = new Int2ObjectArrayMap<>();
    for (int i = 0; i < types.length; i++) {
      DexType applied = mapping.apply(types[i]);
      if (applied != types[i]) {
        changed.put(i, applied);
      }
    }
    return changed.isEmpty()
        ? types
        : ArrayUtils.copyWithSparseChanges(DexType[].class, types, changed);
  }

  private DexString createShorty(DexType returnType, DexType[] argumentTypes) {
    StringBuilder shortyBuilder = new StringBuilder();
    shortyBuilder.append(returnType.toShorty());
    for (DexType argumentType : argumentTypes) {
      shortyBuilder.append(argumentType.toShorty());
    }
    return createString(shortyBuilder.toString());
  }

  public DexMethod createMethod(DexType holder, DexProto proto, DexString name) {
    assert !sorted;
    DexMethod method = new DexMethod(holder, proto, name, skipNameValidationForTesting);
    return canonicalize(methods, method);
  }

  public DexMethod createMethod(DexType holder, DexProto proto, String name) {
    return createMethod(holder, proto, createString(name));
  }

  public DexMethodHandle createMethodHandle(
      MethodHandleType type,
      Descriptor<? extends DexItem, ? extends Descriptor<?, ?>> fieldOrMethod) {
    assert !sorted;
    DexMethodHandle methodHandle = new DexMethodHandle(type, fieldOrMethod);
    return canonicalize(methodHandles, methodHandle);
  }

  public DexCallSite createCallSite(
      DexString methodName,
      DexProto methodProto,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapArgs) {
    // Call sites are never equal and therefore we do not canonicalize.
    assert !sorted;
    return new DexCallSite(methodName, methodProto, bootstrapMethod, bootstrapArgs);
  }

  public DexMethod createMethod(DexString clazzDescriptor, DexString name,
      DexString returnTypeDescriptor,
      DexString[] parameterDescriptors) {
    assert !sorted;
    DexType clazz = createType(clazzDescriptor);
    DexType returnType = createType(returnTypeDescriptor);
    DexType[] parameterTypes = new DexType[parameterDescriptors.length];
    for (int i = 0; i < parameterDescriptors.length; i++) {
      parameterTypes[i] = createType(parameterDescriptors[i]);
    }
    DexProto proto = createProto(returnType, parameterTypes);

    return createMethod(clazz, proto, name);
  }

  public AdvanceLine createAdvanceLine(int delta) {
    synchronized (advanceLines) {
      return advanceLines.computeIfAbsent(delta, AdvanceLine::new);
    }
  }

  public AdvancePC createAdvancePC(int delta) {
    synchronized (advancePCs) {
      return advancePCs.computeIfAbsent(delta, AdvancePC::new);
    }
  }

  public Default createDefault(int value) {
    synchronized (defaults) {
      return defaults.computeIfAbsent(value, Default::new);
    }
  }

  public EndLocal createEndLocal(int registerNum) {
    synchronized (endLocals) {
      return endLocals.computeIfAbsent(registerNum, EndLocal::new);
    }
  }

  public RestartLocal createRestartLocal(int registerNum) {
    synchronized (restartLocals) {
      return restartLocals.computeIfAbsent(registerNum, RestartLocal::new);
    }
  }

  public SetEpilogueBegin createSetEpilogueBegin() {
    return setEpilogueBegin;
  }

  public SetPrologueEnd createSetPrologueEnd() {
    return setPrologueEnd;
  }

  public SetFile createSetFile(DexString fileName) {
    synchronized (setFiles) {
      return setFiles.computeIfAbsent(fileName, SetFile::new);
    }
  }

  // TODO(tamaskenez) b/69024229 Measure if canonicalization is worth it.
  public SetInlineFrame createSetInlineFrame(DexMethod callee, Position caller) {
    synchronized (setInlineFrames) {
      return setInlineFrames.computeIfAbsent(new SetInlineFrame(callee, caller), p -> p);
    }
  }

  public boolean isConstructor(DexMethod method) {
    return method.name == constructorMethodName;
  }

  public boolean isClassConstructor(DexMethod method) {
    return method.name == classConstructorMethodName;
  }

  public ReferenceTypeLatticeElement createReferenceTypeLatticeElement(
      DexType type, boolean isNullable, AppInfo appInfo) {
    ReferenceTypeLatticeElement typeLattice = referenceTypeLatticeElements.get(type);
    if (typeLattice != null) {
      return isNullable == typeLattice.isNullable() ? typeLattice
          : typeLattice.getOrCreateDualLattice();
    }
    synchronized (type) {
      typeLattice = referenceTypeLatticeElements.get(type);
      if (typeLattice == null) {
        if (type.isClassType()) {
          if (!type.isUnknown() && type.isInterface()) {
            typeLattice = new ClassTypeLatticeElement(
                appInfo.dexItemFactory.objectType, isNullable, ImmutableSet.of(type));
          } else {
            // In theory, `interfaces` is the least upper bound of implemented interfaces.
            // It is expensive to walk through type hierarchy; collect implemented interfaces; and
            // compute the least upper bound of two interface sets. Hence, lazy computations.
            // Most likely during lattice join. See {@link ClassTypeLatticeElement#getInterfaces}.
            typeLattice = new ClassTypeLatticeElement(type, isNullable, appInfo);
          }
        } else {
          assert type.isArrayType();
          typeLattice = new ArrayTypeLatticeElement(type, isNullable);
        }
        referenceTypeLatticeElements.put(type, typeLattice);
      }
    }
    return typeLattice;
  }

  private static <S extends PresortedComparable<S>> void assignSortedIndices(Collection<S> items,
      NamingLens namingLens) {
    List<S> sorted = new ArrayList<>(items);
    sorted.sort((a, b) -> a.layeredCompareTo(b, namingLens));
    int i = 0;
    for (S value : sorted) {
      value.setSortedIndex(i++);
    }
  }

  synchronized public void sort(NamingLens namingLens) {
    assert !sorted;
    assignSortedIndices(strings.values(), namingLens);
    assignSortedIndices(types.values(), namingLens);
    assignSortedIndices(fields.values(), namingLens);
    assignSortedIndices(protos.values(), namingLens);
    assignSortedIndices(methods.values(), namingLens);
    sorted = true;
  }

  synchronized public void resetSortedIndices() {
    if (!sorted) {
      return;
    }
    // Only used for asserting that we don't use the sorted index after we build the graph.
    strings.values().forEach(IndexedDexItem::resetSortedIndex);
    types.values().forEach(IndexedDexItem::resetSortedIndex);
    fields.values().forEach(IndexedDexItem::resetSortedIndex);
    protos.values().forEach(IndexedDexItem::resetSortedIndex);
    methods.values().forEach(IndexedDexItem::resetSortedIndex);
    sorted = false;
  }

  synchronized public void forAllTypes(Consumer<DexType> f) {
    new ArrayList<>(types.values()).forEach(f);
  }
}
