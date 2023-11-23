// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.analysis.type.ClassTypeElement.computeLeastUpperBoundOfInterfaces;
import static com.android.tools.r8.ir.desugar.LambdaClass.LAMBDA_INSTANCE_FIELD_NAME;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexDebugEvent.AdvanceLine;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetPositionFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DequeUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.LRUCacheTable;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DexItemFactory {

  public static final String throwableDescriptorString = "Ljava/lang/Throwable;";
  public static final String dalvikAnnotationSignatureString = "Ldalvik/annotation/Signature;";
  public static final String recordTagDescriptorString = "Lcom/android/tools/r8/RecordTag;";
  public static final String recordDescriptorString = "Ljava/lang/Record;";
  public static final String desugarVarHandleDescriptorString =
      "Lcom/android/tools/r8/DesugarVarHandle;";
  public static final String varHandleDescriptorString = "Ljava/lang/invoke/VarHandle;";
  public static final String desugarMethodHandlesLookupDescriptorString =
      "Lcom/android/tools/r8/DesugarMethodHandlesLookup;";
  public static final String methodHandlesLookupDescriptorString =
      "Ljava/lang/invoke/MethodHandles$Lookup;";
  public static final String dalvikAnnotationOptimizationPrefixString =
      "Ldalvik/annotation/optimization/";
  public static final String androidUtilSparseArrayDescriptorString = "Landroid/util/SparseArray;";
  public static final String androidContentResTypedArrayDescriptorString =
      "Landroid/content/res/TypedArray;";
  public static final String androidContentContentProviderClientDescriptorString =
      "Landroid/content/ContentProviderClient;";
  public static final String androidDrmDrmManagerClientDescriptorString =
      "Landroid/drm/DrmManagerClient;";
  public static final String androidMediaMediaDrmDescriptorString = "Landroid/media/MediaDrm;";
  public static final String androidMediaMediaMetadataRetrieverDescriptorString =
      "Landroid/media/MediaMetadataRetriever;";
  public static final String androidResourcesDescriptorString = "Landroid/content/res/Resources;";

  /** Set of types that may be synthesized during compilation. */
  private final Set<DexType> possibleCompilerSynthesizedTypes = Sets.newIdentityHashSet();

  private final Map<DexString, DexString> markers = new ConcurrentHashMap<>();
  private final Map<DexString, DexString> strings = new ConcurrentHashMap<>();
  private final Map<DexString, DexType> types = new ConcurrentHashMap<>();
  private final Map<DexField, DexField> fields = new ConcurrentHashMap<>();
  private final Map<DexProto, DexProto> protos = new ConcurrentHashMap<>();
  private final Map<DexMethod, DexMethod> methods = new ConcurrentHashMap<>();
  private final Map<DexMethodHandle, DexMethodHandle> methodHandles =
      new ConcurrentHashMap<>();

  // DexDebugEvent Canonicalization.
  private final Int2ReferenceMap<AdvanceLine> advanceLines = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<AdvancePC> advancePCs = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<Default> defaults = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<EndLocal> endLocals = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<RestartLocal> restartLocals = new Int2ReferenceOpenHashMap<>();
  private final SetEpilogueBegin setEpilogueBegin = new SetEpilogueBegin();
  private final SetPrologueEnd setPrologueEnd = new SetPrologueEnd();
  private final Map<DexString, SetFile> setFiles = new HashMap<>();
  private final Map<SetPositionFrame, SetPositionFrame> setInlineFrames = new HashMap<>();
  public final DexDebugEvent.Default zeroChangeDefaultEvent = createDefault(0, 0);
  public final DexDebugEvent.Default oneChangeDefaultEvent = createDefault(1, 1);

  // ReferenceTypeElement canonicalization.
  private final ConcurrentHashMap<DexType, ReferenceTypeElement> referenceTypes =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexType, InterfaceCollection> classTypeInterfaces =
      new ConcurrentHashMap<>();
  public final LRUCacheTable<InterfaceCollection, InterfaceCollection, InterfaceCollection>
      leastUpperBoundOfInterfacesTable = LRUCacheTable.create(8, 8);
  boolean sorted = false;

  // Internal type containing only the null value.
  public static final DexType nullValueType = new DexType(new DexString("NULL"));

  public static final DexString unknownTypeName = new DexString("UNKNOWN");

  private static final IdentityHashMap<DexItem, DexItem> internalSentinels =
      new IdentityHashMap<>(
          ImmutableMap.of(
              nullValueType, nullValueType,
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
  public final DexString descriptorSeparator = createString("/");
  public final DexString comSunDescriptorPrefix = createString("Lcom/sun/");
  public final DexString javaDescriptorPrefix = createString("Ljava/");
  public final DexString javaxDescriptorPrefix = createString("Ljavax/");
  public final DexString jdkDescriptorPrefix = createString("Ljdk/");
  public final DexString sunDescriptorPrefix = createString("Lsun/");
  public final DexString jDollarDescriptorPrefix = createString("Lj$/");

  private final DexString booleanArrayDescriptor = createString("[Z");
  private final DexString byteArrayDescriptor = createString("[B");
  private final DexString charArrayDescriptor = createString("[C");
  private final DexString doubleArrayDescriptor = createString("[D");
  private final DexString floatArrayDescriptor = createString("[F");
  private final DexString intArrayDescriptor = createString("[I");
  private final DexString longArrayDescriptor = createString("[J");
  private final DexString shortArrayDescriptor = createString("[S");

  public final DexString boxedBooleanDescriptor = createString("Ljava/lang/Boolean;");
  public final DexString boxedByteDescriptor = createString("Ljava/lang/Byte;");
  public final DexString boxedCharDescriptor = createString("Ljava/lang/Character;");
  public final DexString boxedDoubleDescriptor = createString("Ljava/lang/Double;");
  public final DexString boxedFloatDescriptor = createString("Ljava/lang/Float;");
  public final DexString boxedIntDescriptor = createString("Ljava/lang/Integer;");
  public final DexString boxedLongDescriptor = createString("Ljava/lang/Long;");
  public final DexString boxedShortDescriptor = createString("Ljava/lang/Short;");
  public final DexString boxedNumberDescriptor = createString("Ljava/lang/Number;");
  public final DexString boxedVoidDescriptor = createString("Ljava/lang/Void;");

  public final DexString waitMethodName = createString("wait");
  public final DexString notifyMethodName = createString("notify");
  public final DexString notifyAllMethodName = createString("notifyAll");

  public final DexString unboxBooleanMethodName = createString("booleanValue");
  public final DexString unboxByteMethodName = createString("byteValue");
  public final DexString unboxCharMethodName = createString("charValue");
  public final DexString unboxShortMethodName = createString("shortValue");
  public final DexString unboxIntMethodName = createString("intValue");
  public final DexString unboxLongMethodName = createString("longValue");
  public final DexString unboxFloatMethodName = createString("floatValue");
  public final DexString unboxDoubleMethodName = createString("doubleValue");

  public final DexString isEmptyMethodName = createString("isEmpty");
  public final DexString lengthMethodName = createString("length");

  public final DexString concatMethodName = createString("concat");
  public final DexString containsMethodName = createString("contains");
  public final DexString startsWithMethodName = createString("startsWith");
  public final DexString endsWithMethodName = createString("endsWith");
  public final DexString equalsMethodName = createString("equals");
  public final DexString hashCodeMethodName = createString("hashCode");
  public final DexString identityHashCodeName = createString("identityHashCode");
  public final DexString equalsIgnoreCaseMethodName = createString("equalsIgnoreCase");
  public final DexString contentEqualsMethodName = createString("contentEquals");
  public final DexString indexOfMethodName = createString("indexOf");
  public final DexString lastIndexOfMethodName = createString("lastIndexOf");
  public final DexString compareToMethodName = createString("compareTo");
  public final DexString compareToIgnoreCaseMethodName = createString("compareToIgnoreCase");
  public final DexString cloneMethodName = createString("clone");
  public final DexString formatMethodName = createString("format");
  public final DexString substringName = createString("substring");
  public final DexString trimName = createString("trim");

  public final DexString valueOfMethodName = createString("valueOf");
  public final DexString valuesMethodName = createString("values");
  public final DexString toStringMethodName = createString("toString");
  public final DexString internMethodName = createString("intern");

  public final DexString convertMethodName = createString("convert");
  public final DexString wrapperFieldName = createString("wrappedValue");

  public final DexString getClassMethodName = createString("getClass");
  public final DexString finalizeMethodName = createString("finalize");
  public final DexString ordinalMethodName = createString("ordinal");
  public final DexString nameMethodName = createString("name");
  public final DexString desiredAssertionStatusMethodName = createString("desiredAssertionStatus");
  public final DexString forNameMethodName = createString("forName");
  public final DexString getNameName = createString("getName");
  public final DexString getCanonicalNameName = createString("getCanonicalName");
  public final DexString getSimpleNameName = createString("getSimpleName");
  public final DexString getTypeNameName = createString("getTypeName");
  public final DexString getDeclaredConstructorName = createString("getDeclaredConstructor");
  public final DexString getFieldName = createString("getField");
  public final DexString getDeclaredFieldName = createString("getDeclaredField");
  public final DexString getMethodName = createString("getMethod");
  public final DexString getDeclaredMethodName = createString("getDeclaredMethod");
  public final DexString newInstanceName = createString("newInstance");
  public final DexString assertionsDisabled = createString("$assertionsDisabled");
  public final DexString invokeMethodName = createString("invoke");
  public final DexString invokeExactMethodName = createString("invokeExact");

  public final DexString runtimeExceptionDescriptor = createString("Ljava/lang/RuntimeException;");
  public final DexString assertionErrorDescriptor = createString("Ljava/lang/AssertionError;");
  public final DexString charSequenceDescriptor = createString("Ljava/lang/CharSequence;");
  public final DexString charSequenceArrayDescriptor = createString("[Ljava/lang/CharSequence;");
  public final DexString stringDescriptor = createString("Ljava/lang/String;");
  public final DexString stringArrayDescriptor = createString("[Ljava/lang/String;");
  public final DexString objectDescriptor = createString("Ljava/lang/Object;");
  public final DexString recordDescriptor = createString(recordDescriptorString);
  public final DexString recordTagDescriptor = createString(recordTagDescriptorString);
  public final DexString objectArrayDescriptor = createString("[Ljava/lang/Object;");
  public final DexString classDescriptor = createString("Ljava/lang/Class;");
  public final DexString classLoaderDescriptor = createString("Ljava/lang/ClassLoader;");
  public final DexString autoCloseableDescriptor = createString("Ljava/lang/AutoCloseable;");
  public final DexString classArrayDescriptor = createString("[Ljava/lang/Class;");
  public final DexString constructorDescriptor = createString("Ljava/lang/reflect/Constructor;");
  public final DexString fieldDescriptor = createString("Ljava/lang/reflect/Field;");
  public final DexString methodDescriptor = createString("Ljava/lang/reflect/Method;");
  public final DexString enumDescriptor = createString("Ljava/lang/Enum;");
  public final DexString javaLangSystemDescriptor = createString("Ljava/lang/System;");
  public final DexString annotationDescriptor = createString("Ljava/lang/annotation/Annotation;");
  public final DexString objectsDescriptor = createString("Ljava/util/Objects;");
  public final DexString collectionsDescriptor = createString("Ljava/util/Collections;");
  public final DexString iterableDescriptor = createString("Ljava/lang/Iterable;");
  public final DexString mathDescriptor = createString("Ljava/lang/Math;");
  public final DexString strictMathDescriptor = createString("Ljava/lang/StrictMath;");
  public final DexString closeableDescriptor = createString("Ljava/io/Closeable;");
  public final DexString zipFileDescriptor = createString("Ljava/util/zip/ZipFile;");

  public final DexString bufferDescriptor = createString("Ljava/nio/Buffer;");
  public final DexString byteBufferDescriptor = createString("Ljava/nio/ByteBuffer;");
  public final DexString mappedByteBufferDescriptor = createString("Ljava/nio/MappedByteBuffer;");
  public final DexString charBufferDescriptor = createString("Ljava/nio/CharBuffer;");
  public final DexString shortBufferDescriptor = createString("Ljava/nio/ShortBuffer;");
  public final DexString intBufferDescriptor = createString("Ljava/nio/IntBuffer;");
  public final DexString longBufferDescriptor = createString("Ljava/nio/LongBuffer;");
  public final DexString floatBufferDescriptor = createString("Ljava/nio/FloatBuffer;");
  public final DexString doubleBufferDescriptor = createString("Ljava/nio/DoubleBuffer;");

  public final DexString stringBuilderDescriptor = createString("Ljava/lang/StringBuilder;");
  public final DexString stringBufferDescriptor = createString("Ljava/lang/StringBuffer;");

  public final DexString varHandleDescriptor = createString(varHandleDescriptorString);
  public final DexString methodHandleDescriptor = createString("Ljava/lang/invoke/MethodHandle;");
  public final DexString methodHandlesDescriptor = createString("Ljava/lang/invoke/MethodHandles;");
  public final DexString methodHandlesLookupDescriptor =
      createString(methodHandlesLookupDescriptorString);
  public final DexString methodTypeDescriptor = createString("Ljava/lang/invoke/MethodType;");
  public final DexString invocationHandlerDescriptor =
      createString("Ljava/lang/reflect/InvocationHandler;");
  public final DexString proxyDescriptor = createString("Ljava/lang/reflect/Proxy;");
  public final DexString serviceLoaderDescriptor = createString("Ljava/util/ServiceLoader;");
  public final DexString serviceLoaderConfigurationErrorDescriptor =
      createString("Ljava/util/ServiceConfigurationError;");
  public final DexString localeDescriptor = createString("Ljava/util/Locale;");
  public final DexString listDescriptor = createString("Ljava/util/List;");
  public final DexString setDescriptor = createString("Ljava/util/Set;");
  public final DexString mapDescriptor = createString("Ljava/util/Map;");
  public final DexString mapEntryDescriptor = createString("Ljava/util/Map$Entry;");
  public final DexString collectionDescriptor = createString("Ljava/util/Collection;");
  public final DexString comparatorDescriptor = createString("Ljava/util/Comparator;");
  public final DexString callableDescriptor = createString("Ljava/util/concurrent/Callable;");
  public final DexString supplierDescriptor = createString("Ljava/util/function/Supplier;");
  public final DexString predicateDescriptor = createString("Ljava/util/function/Predicate;");
  public final DexString consumerDescriptor = createString("Ljava/util/function/Consumer;");
  public final DexString runnableDescriptor = createString("Ljava/lang/Runnable;");
  public final DexString optionalDescriptor = createString("Ljava/util/Optional;");
  public final DexString optionalDoubleDescriptor = createString("Ljava/util/OptionalDouble;");
  public final DexString optionalIntDescriptor = createString("Ljava/util/OptionalInt;");
  public final DexString optionalLongDescriptor = createString("Ljava/util/OptionalLong;");
  public final DexString streamDescriptor = createString("Ljava/util/stream/Stream;");
  public final DexString arraysDescriptor = createString("Ljava/util/Arrays;");
  public final DexString threadLocalDescriptor = createString("Ljava/lang/ThreadLocal;");
  public final DexString concurrentHashMapDescriptor =
      createString("Ljava/util/concurrent/ConcurrentHashMap;");
  public final DexString concurrentHaspMapKeySetViewDescriptor =
      createString("Ljava/util/concurrent/ConcurrentHashMap$KeySetView;");

  public final DexString throwableDescriptor = createString(throwableDescriptorString);
  public final DexString illegalAccessErrorDescriptor =
      createString("Ljava/lang/IllegalAccessError;");
  public final DexString illegalArgumentExceptionDescriptor =
      createString("Ljava/lang/IllegalArgumentException;");
  public final DexString abstractMethodErrorDescriptor =
      createString("Ljava/lang/AbstractMethodError;");
  public final DexString icceDescriptor = createString("Ljava/lang/IncompatibleClassChangeError;");
  public final DexString exceptionInInitializerErrorDescriptor =
      createString("Ljava/lang/ExceptionInInitializerError;");
  public final DexString noClassDefFoundErrorDescriptor =
      createString("Ljava/lang/NoClassDefFoundError;");
  public final DexString noSuchFieldErrorDescriptor = createString("Ljava/lang/NoSuchFieldError;");
  public final DexString npeDescriptor = createString("Ljava/lang/NullPointerException;");
  public final DexString reflectiveOperationExceptionDescriptor =
      createString("Ljava/lang/ReflectiveOperationException;");
  public final DexString kotlinMetadataDescriptor = createString("Lkotlin/Metadata;");
  public final DexString kotlinJvmNameDescriptor = createString("Lkotlin/jvm/JvmName;");

  public final DexString intFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;");
  public final DexString longFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicLongFieldUpdater;");
  public final DexString referenceFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;");
  public final DexString newUpdaterName = createString("newUpdater");

  public final DexString constructorMethodName = createString(Constants.INSTANCE_INITIALIZER_NAME);
  public final DexString classConstructorMethodName =
      createString(Constants.CLASS_INITIALIZER_NAME);
  public final DexString temporaryConstructorMethodPrefix =
      createString(Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX);
  public final DexString syntheticConstructorMethodPrefix =
      createString(Constants.SYNTHETIC_INSTANCE_INITIALIZER_PREFIX);

  public final DexString thisName = createString("this");
  public final DexString lambdaInstanceFieldName = createString(LAMBDA_INSTANCE_FIELD_NAME);
  public final DexString javacLambdaMethodPrefix =
      createString(LambdaClass.JAVAC_EXPECTED_LAMBDA_METHOD_PREFIX);

  // As much as possible, R8 should rely on the content of the static enum field, using
  // enumMembers.isValuesFieldCandidate or checking the object state in the optimization info.
  // The field name is unrealiable since the filed can be minified prior to this compilation.
  // We keep enumValuesFieldName as a heuristic only.
  public final DexString enumValuesFieldName = createString("$VALUES");

  public final DexString enabledFieldName = createString("ENABLED");

  public final DexString throwableArrayDescriptor = createString("[Ljava/lang/Throwable;");

  public final DexString valueString = createString("value");
  public final DexString kindString = createString("kind");
  public final DexString versionHashString = createString("versionHash");
  public final DexString apiLevelString = createString("apiLevel");

  // Prefix for runtime affecting yet potential class-retained annotations.
  public final DexString dalvikAnnotationOptimizationPrefix =
      createString(dalvikAnnotationOptimizationPrefixString);

  // Method names used on VarHandle.
  public final DexString getString = createString("get");
  public final DexString setString = createString("set");
  public final DexString compareAndSetString = createString("compareAndSet");
  public final DexString weakCompareAndSetString = createString("weakCompareAndSet");
  public final DexString getVolatileString = createString("getVolatile");
  public final DexString setVolatileString = createString("setVolatile");
  public final DexString setReleaseString = createString("setRelease");

  // Method names used on MethodHandles.
  public final DexString lookupString = createString("lookup");
  public final DexString privateLookupInString = createString("privateLookupIn");

  public final DexType booleanType = createStaticallyKnownType(booleanDescriptor);
  public final DexType byteType = createStaticallyKnownType(byteDescriptor);
  public final DexType charType = createStaticallyKnownType(charDescriptor);
  public final DexType doubleType = createStaticallyKnownType(doubleDescriptor);
  public final DexType floatType = createStaticallyKnownType(floatDescriptor);
  public final DexType intType = createStaticallyKnownType(intDescriptor);
  public final DexType longType = createStaticallyKnownType(longDescriptor);
  public final DexType shortType = createStaticallyKnownType(shortDescriptor);
  public final DexType voidType = createStaticallyKnownType(voidDescriptor);

  public final DexType booleanArrayType = createStaticallyKnownType(booleanArrayDescriptor);
  public final DexType byteArrayType = createStaticallyKnownType(byteArrayDescriptor);
  public final DexType charArrayType = createStaticallyKnownType(charArrayDescriptor);
  public final DexType doubleArrayType = createStaticallyKnownType(doubleArrayDescriptor);
  public final DexType floatArrayType = createStaticallyKnownType(floatArrayDescriptor);
  public final DexType intArrayType = createStaticallyKnownType(intArrayDescriptor);
  public final DexType longArrayType = createStaticallyKnownType(longArrayDescriptor);
  public final DexType shortArrayType = createStaticallyKnownType(shortArrayDescriptor);

  public final DexType boxedBooleanType = createStaticallyKnownType(boxedBooleanDescriptor);
  public final DexType boxedByteType = createStaticallyKnownType(boxedByteDescriptor);
  public final DexType boxedCharType = createStaticallyKnownType(boxedCharDescriptor);
  public final DexType boxedDoubleType = createStaticallyKnownType(boxedDoubleDescriptor);
  public final DexType boxedFloatType = createStaticallyKnownType(boxedFloatDescriptor);
  public final DexType boxedIntType = createStaticallyKnownType(boxedIntDescriptor);
  public final DexType boxedLongType = createStaticallyKnownType(boxedLongDescriptor);
  public final DexType boxedShortType = createStaticallyKnownType(boxedShortDescriptor);
  public final DexType boxedNumberType = createStaticallyKnownType(boxedNumberDescriptor);
  public final DexType boxedVoidType = createStaticallyKnownType(boxedVoidDescriptor);

  public final DexType charSequenceType = createStaticallyKnownType(charSequenceDescriptor);
  public final DexType charSequenceArrayType =
      createStaticallyKnownType(charSequenceArrayDescriptor);
  public final DexType stringType = createStaticallyKnownType(stringDescriptor);
  public final DexType stringArrayType = createStaticallyKnownType(stringArrayDescriptor);
  public final DexType objectType = createStaticallyKnownType(objectDescriptor);
  public final DexType recordType = createStaticallyKnownType(recordDescriptor);
  public final DexType recordTagType = createStaticallyKnownType(recordTagDescriptor);
  public final DexType objectArrayType = createStaticallyKnownType(objectArrayDescriptor);
  public final DexType classArrayType = createStaticallyKnownType(classArrayDescriptor);
  public final DexType enumType = createStaticallyKnownType(enumDescriptor);
  public final DexType annotationType = createStaticallyKnownType(annotationDescriptor);
  public final DexType objectsType = createStaticallyKnownType(objectsDescriptor);
  public final DexType collectionsType = createStaticallyKnownType(collectionsDescriptor);
  public final DexType iterableType = createStaticallyKnownType(iterableDescriptor);
  public final DexType mathType = createStaticallyKnownType(mathDescriptor);
  public final DexType strictMathType = createStaticallyKnownType(strictMathDescriptor);
  public final DexType referenceFieldUpdaterType =
      createStaticallyKnownType(referenceFieldUpdaterDescriptor);

  public final DexType classType = createStaticallyKnownType(classDescriptor);
  public final DexType packageType = createStaticallyKnownType(Package.class);
  public final DexType classLoaderType = createStaticallyKnownType(classLoaderDescriptor);
  public final DexType constructorType = createStaticallyKnownType(constructorDescriptor);
  public final DexType fieldType = createStaticallyKnownType(fieldDescriptor);
  public final DexType methodType = createStaticallyKnownType(methodDescriptor);
  public final DexType autoCloseableType = createStaticallyKnownType(autoCloseableDescriptor);

  public final DexType closeableType = createStaticallyKnownType(closeableDescriptor);
  public final DexType zipFileType = createStaticallyKnownType(zipFileDescriptor);

  public final DexType stringBuilderType = createStaticallyKnownType(stringBuilderDescriptor);
  public final DexType stringBufferType = createStaticallyKnownType(stringBufferDescriptor);

  public final DexType javaLangAnnotationRetentionPolicyType =
      createStaticallyKnownType("Ljava/lang/annotation/RetentionPolicy;");
  public final DexType javaLangReflectArrayType =
      createStaticallyKnownType("Ljava/lang/reflect/Array;");
  public final DexType javaLangSystemType = createStaticallyKnownType(javaLangSystemDescriptor);
  public final DexType javaIoPrintStreamType = createStaticallyKnownType("Ljava/io/PrintStream;");

  public final DexType varHandleType = createStaticallyKnownType(varHandleDescriptor);
  public final DexType methodHandleType = createStaticallyKnownType(methodHandleDescriptor);
  public final DexType methodHandlesType = createStaticallyKnownType(methodHandlesDescriptor);
  public final DexType methodHandlesLookupType =
      createStaticallyKnownType(methodHandlesLookupDescriptor);
  public final DexType methodTypeType = createStaticallyKnownType(methodTypeDescriptor);
  public final DexType invocationHandlerType =
      createStaticallyKnownType(invocationHandlerDescriptor);
  public final DexType proxyType = createStaticallyKnownType(proxyDescriptor);
  public final DexType serviceLoaderType = createStaticallyKnownType(serviceLoaderDescriptor);
  public final DexType serviceLoaderConfigurationErrorType =
      createStaticallyKnownType(serviceLoaderConfigurationErrorDescriptor);
  public final DexType listType = createStaticallyKnownType(listDescriptor);
  public final DexType setType = createStaticallyKnownType(setDescriptor);
  public final DexType mapType = createStaticallyKnownType(mapDescriptor);
  public final DexType mapEntryType = createStaticallyKnownType(mapEntryDescriptor);
  public final DexType abstractMapSimpleEntryType =
      createStaticallyKnownType("Ljava/util/AbstractMap$SimpleEntry;");
  public final DexType collectionType = createStaticallyKnownType(collectionDescriptor);
  public final DexType comparatorType = createStaticallyKnownType(comparatorDescriptor);
  public final DexType callableType = createStaticallyKnownType(callableDescriptor);
  public final DexType supplierType = createStaticallyKnownType(supplierDescriptor);
  public final DexType predicateType = createStaticallyKnownType(predicateDescriptor);
  public final DexType consumerType = createStaticallyKnownType(consumerDescriptor);
  public final DexType runnableType = createStaticallyKnownType(runnableDescriptor);
  public final DexType optionalType = createStaticallyKnownType(optionalDescriptor);
  public final DexType optionalDoubleType = createStaticallyKnownType(optionalDoubleDescriptor);
  public final DexType optionalIntType = createStaticallyKnownType(optionalIntDescriptor);
  public final DexType optionalLongType = createStaticallyKnownType(optionalLongDescriptor);
  public final DexType streamType = createStaticallyKnownType(streamDescriptor);
  public final DexType threadLocalType = createStaticallyKnownType(threadLocalDescriptor);
  public final DexType concurrentHashMapType =
      createStaticallyKnownType(concurrentHashMapDescriptor);
  public final DexType concurrentHashMapKeySetViewType =
      createStaticallyKnownType(concurrentHaspMapKeySetViewDescriptor);

  public final DexType bufferType = createStaticallyKnownType(bufferDescriptor);
  public final DexType byteBufferType = createStaticallyKnownType(byteBufferDescriptor);
  public final DexType mappedByteBufferType = createStaticallyKnownType(mappedByteBufferDescriptor);
  public final DexType charBufferType = createStaticallyKnownType(charBufferDescriptor);
  public final DexType shortBufferType = createStaticallyKnownType(shortBufferDescriptor);
  public final DexType intBufferType = createStaticallyKnownType(intBufferDescriptor);
  public final DexType longBufferType = createStaticallyKnownType(longBufferDescriptor);
  public final DexType floatBufferType = createStaticallyKnownType(floatBufferDescriptor);
  public final DexType doubleBufferType = createStaticallyKnownType(doubleBufferDescriptor);
  public final List<DexType> typeSpecificBuffers =
      ImmutableList.of(
          byteBufferType,
          mappedByteBufferType,
          charBufferType,
          shortBufferType,
          intBufferType,
          longBufferType,
          floatBufferType,
          doubleBufferType);

  private static final List<String> MULTIDEX_PREFIXES =
      ImmutableList.of("androidx/", "android/support/");
  private static final List<String> MULTIDEX_SUFFIXES =
      ImmutableList.of(
          "multidex/MultiDex$V14$ElementConstructor;",
          "multidex/MultiDex$V14$ICSElementConstructor;",
          "multidex/MultiDex$V14$JBMR11ElementConstructor;",
          "multidex/MultiDex$V14$JBMR2ElementConstructor;",
          "multidex/MultiDex$V14;",
          "multidex/MultiDex$V19;",
          "multidex/MultiDex$V21_PLUS;",
          "multidex/MultiDex$V4;",
          "multidex/MultiDexApplication;",
          "multidex/MultiDexExtractor$1;",
          "multidex/MultiDexExtractor$ExtractedDex;",
          "multidex/MultiDexExtractor;",
          "multidex/MultiDex;",
          "multidex/ZipUtil;",
          "multidex/ZipUtil$CentralDirectory;");
  private static final List<String> MULTIDEX_INSTRUMENTATION =
      ImmutableList.of(
          "Landroid/support/multidex/instrumentation/BuildConfig;",
          "Landroid/test/runner/MultiDexTestRunner;");

  private List<DexType> createMultiDexTypes() {
    ImmutableList.Builder<DexType> builder = ImmutableList.builder();
    for (String prefix : MULTIDEX_PREFIXES) {
      for (String suffix : MULTIDEX_SUFFIXES) {
        builder.add(createType("L" + prefix + suffix));
      }
    }
    for (String typeString : MULTIDEX_INSTRUMENTATION) {
      builder.add(createType(typeString));
    }
    return builder.build();
  }

  public List<DexType> multiDexTypes = createMultiDexTypes();

  public final DexType doubleConsumer =
      createStaticallyKnownType("Ljava/util/function/DoubleConsumer;");
  public final DexType longConsumer =
      createStaticallyKnownType("Ljava/util/function/LongConsumer;");
  public final DexType intConsumer = createStaticallyKnownType("Ljava/util/function/IntConsumer;");

  public final DexType retentionType =
      createStaticallyKnownType("Ljava/lang/annotation/Retention;");
  public final DexType runtimeExceptionType = createStaticallyKnownType(runtimeExceptionDescriptor);
  public final DexType assertionErrorType = createStaticallyKnownType(assertionErrorDescriptor);
  public final DexType throwableType = createStaticallyKnownType(throwableDescriptor);
  public final DexType illegalAccessErrorType =
      createStaticallyKnownType(illegalAccessErrorDescriptor);
  public final DexType illegalArgumentExceptionType =
      createStaticallyKnownType(illegalArgumentExceptionDescriptor);
  public final DexType abstractMethodErrorType =
      createStaticallyKnownType(abstractMethodErrorDescriptor);
  public final DexType icceType = createStaticallyKnownType(icceDescriptor);
  public final DexType exceptionInInitializerErrorType =
      createStaticallyKnownType(exceptionInInitializerErrorDescriptor);
  public final DexType noClassDefFoundErrorType =
      createStaticallyKnownType(noClassDefFoundErrorDescriptor);
  public final DexType noSuchFieldErrorType = createStaticallyKnownType(noSuchFieldErrorDescriptor);
  public final DexType noSuchMethodErrorType =
      createStaticallyKnownType("Ljava/lang/NoSuchMethodError;");
  public final DexType npeType = createStaticallyKnownType(npeDescriptor);
  public final DexType reflectiveOperationExceptionType =
      createStaticallyKnownType(reflectiveOperationExceptionDescriptor);
  public final DexType kotlinMetadataType = createStaticallyKnownType(kotlinMetadataDescriptor);
  public final DexType kotlinJvmNameType = createStaticallyKnownType(kotlinJvmNameDescriptor);

  public final DexType kotlinEnumEntriesList =
      createStaticallyKnownType("Lkotlin/enums/EnumEntriesList;");
  public final DexMethod kotlinEnumEntriesListInit =
      createInstanceInitializer(kotlinEnumEntriesList, createArrayType(1, enumType));

  public final DexType javaIoFileType = createStaticallyKnownType("Ljava/io/File;");
  public final DexType javaMathBigIntegerType = createStaticallyKnownType("Ljava/math/BigInteger;");
  public final DexType javaNioByteOrderType = createStaticallyKnownType("Ljava/nio/ByteOrder;");
  public final DexType javaUtilCollectionsType =
      createStaticallyKnownType("Ljava/util/Collections;");
  public final DexType javaUtilComparatorType = createStaticallyKnownType("Ljava/util/Comparator;");
  public final DexType javaUtilConcurrentTimeUnitType =
      createStaticallyKnownType("Ljava/util/concurrent/TimeUnit;");
  public final DexType javaUtilFormattableType =
      createStaticallyKnownType("Ljava/util/Formattable;");
  public final DexType javaUtilListType = createStaticallyKnownType("Ljava/util/List;");
  public final DexType javaUtilLocaleType = createStaticallyKnownType(localeDescriptor);
  public final DexType javaUtilLoggingLevelType =
      createStaticallyKnownType("Ljava/util/logging/Level;");
  public final DexType javaUtilLoggingLoggerType =
      createStaticallyKnownType("Ljava/util/logging/Logger;");
  public final DexType javaUtilSetType = createStaticallyKnownType("Ljava/util/Set;");

  public final DexType androidAppActivity = createStaticallyKnownType("Landroid/app/Activity;");
  public final DexType androidAppFragment = createStaticallyKnownType("Landroid/app/Fragment;");
  public final DexType androidAppZygotePreload =
      createStaticallyKnownType("Landroid/app/ZygotePreload;");
  public final DexType androidOsBuildType = createStaticallyKnownType("Landroid/os/Build;");
  public final DexType androidOsBuildVersionType =
      createStaticallyKnownType("Landroid/os/Build$VERSION;");
  public final DexType androidOsBundleType = createStaticallyKnownType("Landroid/os/Bundle;");
  public final DexType androidOsParcelableCreatorType =
      createStaticallyKnownType("Landroid/os/Parcelable$Creator;");
  public final DexType androidSystemOsConstantsType =
      createStaticallyKnownType("Landroid/system/OsConstants;");
  public final DexType androidUtilLogType = createStaticallyKnownType("Landroid/util/Log;");
  public final DexType androidUtilPropertyType =
      createStaticallyKnownType("Landroid/util/Property;");
  public final DexType androidViewViewType = createStaticallyKnownType("Landroid/view/View;");
  public final DexType androidUtilSparseArrayType =
      createStaticallyKnownType(androidUtilSparseArrayDescriptorString);
  public final DexType androidContentResTypedArrayType =
      createStaticallyKnownType(androidContentResTypedArrayDescriptorString);
  public final DexType androidContentContentProviderClientType =
      createStaticallyKnownType(androidContentContentProviderClientDescriptorString);
  public final DexType androidDrmDrmManagerClientType =
      createStaticallyKnownType(androidDrmDrmManagerClientDescriptorString);
  public final DexType androidMediaMediaDrmType =
      createStaticallyKnownType(androidMediaMediaDrmDescriptorString);
  public final DexType androidMediaMediaMetadataRetrieverType =
      createStaticallyKnownType(androidMediaMediaMetadataRetrieverDescriptorString);
  public final DexType androidResourcesType =
      createStaticallyKnownType(androidResourcesDescriptorString);
  public final DexString androidResourcesGetStringName = createString("getString");
  public final DexProto androidResourcesGetStringProto = createProto(stringType, intType);
  public final DexMethod androidResourcesGetStringMethod =
      createMethod(
          androidResourcesType, androidResourcesGetStringProto, androidResourcesGetStringName);

  public final StringBuildingMethods stringBuilderMethods =
      new StringBuildingMethods(stringBuilderType);
  public final StringBuildingMethods stringBufferMethods =
      new StringBuildingMethods(stringBufferType);
  public final BooleanMembers booleanMembers = new BooleanMembers();
  public final ByteMembers byteMembers = new ByteMembers();
  public final CharMembers charMembers = new CharMembers();
  public final FloatMembers floatMembers = new FloatMembers();
  public final IntegerMembers integerMembers = new IntegerMembers();
  public final LongMembers longMembers = new LongMembers();
  public final VoidMembers voidMembers = new VoidMembers();
  public final ObjectsMethods objectsMethods = new ObjectsMethods();
  public final ObjectMembers objectMembers = new ObjectMembers();
  public final BufferMembers bufferMembers = new BufferMembers();
  public final RecordMembers recordMembers = new RecordMembers();
  public final ShortMembers shortMembers = new ShortMembers();
  public final StringMembers stringMembers = new StringMembers();
  public final SupplierMembers supplierMembers = new SupplierMembers();
  public final DoubleMembers doubleMembers = new DoubleMembers();
  public final ThrowableMethods throwableMethods = new ThrowableMethods();
  public final AssertionErrorMethods assertionErrorMethods = new AssertionErrorMethods();
  public final ClassMethods classMethods = new ClassMethods();
  public final ConstructorMethods constructorMethods = new ConstructorMethods();
  public final EnumMembers enumMembers = new EnumMembers();
  public final AndroidUtilLogMembers androidUtilLogMembers = new AndroidUtilLogMembers();
  public final JavaLangReflectArrayMembers javaLangReflectArrayMembers =
      new JavaLangReflectArrayMembers();
  public final JavaLangAnnotationRetentionPolicyMembers javaLangAnnotationRetentionPolicyMembers =
      new JavaLangAnnotationRetentionPolicyMembers();
  public final JavaLangSystemMembers javaLangSystemMembers = new JavaLangSystemMembers();
  public final JavaIoPrintStreamMembers javaIoPrintStreamMembers = new JavaIoPrintStreamMembers();
  public final NullPointerExceptionMethods npeMethods = new NullPointerExceptionMethods();
  public final IllegalArgumentExceptionMethods illegalArgumentExceptionMethods =
      new IllegalArgumentExceptionMethods();
  public final PrimitiveTypesBoxedTypeFields primitiveTypesBoxedTypeFields =
      new PrimitiveTypesBoxedTypeFields();
  public final AtomicFieldUpdaterMethods atomicFieldUpdaterMethods =
      new AtomicFieldUpdaterMethods();
  public final Kotlin kotlin;
  public final PolymorphicMethods polymorphicMethods = new PolymorphicMethods();
  public final ProxyMethods proxyMethods = new ProxyMethods();

  // android.**
  public final AndroidOsBuildMembers androidOsBuildMembers = new AndroidOsBuildMembers();
  public final AndroidOsBuildVersionMembers androidOsBuildVersionMembers =
      new AndroidOsBuildVersionMembers();
  public final AndroidOsBundleMembers androidOsBundleMembers = new AndroidOsBundleMembers();
  public final AndroidSystemOsConstantsMembers androidSystemOsConstantsMembers =
      new AndroidSystemOsConstantsMembers();
  public final AndroidViewViewMembers androidViewViewMembers = new AndroidViewViewMembers();
  public final AndroidUtilSparseArrayMembers androidUtilSparseArrayMembers =
      new AndroidUtilSparseArrayMembers();
  public final AndroidContentResTypedArrayMembers androidContentResTypedArrayMembers =
      new AndroidContentResTypedArrayMembers();
  public final AndroidContentContentProviderClientMembers
      androidContentContentProviderClientMembers = new AndroidContentContentProviderClientMembers();
  public final AndroidDrmDrmManagerClientMembers androidDrmDrmManagerClientMembers =
      new AndroidDrmDrmManagerClientMembers();
  public final AndroidMediaMediaDrmMembers androidMediaMediaDrmMembers =
      new AndroidMediaMediaDrmMembers();
  public final AndroidMediaMetadataRetrieverMembers androidMediaMetadataRetrieverMembers =
      new AndroidMediaMetadataRetrieverMembers();

  // java.**
  public final JavaIoFileMembers javaIoFileMembers = new JavaIoFileMembers();
  public final JavaMathBigIntegerMembers javaMathBigIntegerMembers =
      new JavaMathBigIntegerMembers();
  public final JavaNioByteOrderMembers javaNioByteOrderMembers = new JavaNioByteOrderMembers();
  public final JavaUtilArraysMethods javaUtilArraysMethods = new JavaUtilArraysMethods();
  public final JavaUtilComparatorMembers javaUtilComparatorMembers =
      new JavaUtilComparatorMembers();
  public final JavaUtilConcurrentTimeUnitMembers javaUtilConcurrentTimeUnitMembers =
      new JavaUtilConcurrentTimeUnitMembers();
  public final JavaUtilLocaleMembers javaUtilLocaleMembers = new JavaUtilLocaleMembers();
  public final JavaUtilLoggingLevelMembers javaUtilLoggingLevelMembers =
      new JavaUtilLoggingLevelMembers();

  public final List<LibraryMembers> libraryMembersCollection =
      ImmutableList.of(
          booleanMembers,
          floatMembers,
          integerMembers,
          longMembers,
          stringMembers,
          // android.**
          androidOsBuildMembers,
          androidOsBuildVersionMembers,
          androidOsBundleMembers,
          androidSystemOsConstantsMembers,
          androidViewViewMembers,
          // java.**
          javaIoFileMembers,
          javaMathBigIntegerMembers,
          javaNioByteOrderMembers,
          javaUtilComparatorMembers,
          javaUtilConcurrentTimeUnitMembers,
          javaUtilLocaleMembers,
          javaUtilLoggingLevelMembers);

  public final DexString twrCloseResourceMethodName = createString("$closeResource");
  public final DexProto twrCloseResourceMethodProto =
      createProto(voidType, throwableType, autoCloseableType);

  public final DexString deserializeLambdaMethodName = createString("$deserializeLambda$");
  public final DexType serializedLambdaType =
      createStaticallyKnownType("Ljava/lang/invoke/SerializedLambda;");
  public final DexProto deserializeLambdaMethodProto =
      createProto(objectType, serializedLambdaType);

  public final String defaultSourceFileAttributeString = "SourceFile";
  public final DexString defaultSourceFileAttribute =
      createString(defaultSourceFileAttributeString);

  // Dex system annotations.
  // See https://source.android.com/devices/tech/dalvik/dex-format.html#system-annotation
  public final DexType annotationDefault =
      createStaticallyKnownType("Ldalvik/annotation/AnnotationDefault;");
  public final DexType annotationEnclosingClass =
      createStaticallyKnownType("Ldalvik/annotation/EnclosingClass;");
  public final DexType annotationEnclosingMethod =
      createStaticallyKnownType("Ldalvik/annotation/EnclosingMethod;");
  public final DexType annotationInnerClass =
      createStaticallyKnownType("Ldalvik/annotation/InnerClass;");
  public final DexType annotationMemberClasses =
      createStaticallyKnownType("Ldalvik/annotation/MemberClasses;");
  public final DexType annotationMethodParameters =
      createStaticallyKnownType("Ldalvik/annotation/MethodParameters;");
  public final DexType annotationSignature =
      createStaticallyKnownType(dalvikAnnotationSignatureString);
  public final DexType annotationNestHost =
      createStaticallyKnownType("Ldalvik/annotation/NestHost;");
  public final DexType annotationNestMembers =
      createStaticallyKnownType("Ldalvik/annotation/NestMembers;");
  public final DexType annotationPermittedSubclasses =
      createStaticallyKnownType("Ldalvik/annotation/PermittedSubclasses;");
  public final DexType annotationRecord = createStaticallyKnownType("Ldalvik/annotation/Record;");
  public final DexString annotationRecordComponentNames = createString("componentNames");
  public final DexString annotationRecordComponentTypes = createString("componentTypes");
  public final DexString annotationRecordComponentSignatures = createString("componentSignatures");
  public final DexString annotationRecordComponentAnnotationVisibilities =
      createString("componentAnnotationVisibilities");
  public final DexString annotationRecordComponentAnnotations =
      createString("componentAnnotations");
  public final DexType annotationSourceDebugExtension =
      createStaticallyKnownType("Ldalvik/annotation/SourceDebugExtension;");
  public final DexType annotationThrows = createStaticallyKnownType("Ldalvik/annotation/Throws;");
  public final DexType annotationSynthesizedClass =
      createStaticallyKnownType("Lcom/android/tools/r8/annotations/SynthesizedClassV2;");
  public final DexType annotationCovariantReturnType =
      createStaticallyKnownType("Ldalvik/annotation/codegen/CovariantReturnType;");
  public final DexType annotationCovariantReturnTypes =
      createStaticallyKnownType(
          "Ldalvik/annotation/codegen/CovariantReturnType$CovariantReturnTypes;");
  public final DexType annotationReachabilitySensitive =
      createStaticallyKnownType("Ldalvik/annotation/optimization/ReachabilitySensitive;");

  private static final String METAFACTORY_METHOD_NAME = "metafactory";
  private static final String METAFACTORY_ALT_METHOD_NAME = "altMetafactory";

  public final DexType metafactoryType =
      createStaticallyKnownType("Ljava/lang/invoke/LambdaMetafactory;");
  public final DexType constantBootstrapsType =
      createStaticallyKnownType("Ljava/lang/invoke/ConstantBootstraps;");
  public final DexType callSiteType = createStaticallyKnownType("Ljava/lang/invoke/CallSite;");
  public final DexType lookupType =
      createStaticallyKnownType("Ljava/lang/invoke/MethodHandles$Lookup;");
  public final DexType objectMethodsType =
      createStaticallyKnownType("Ljava/lang/runtime/ObjectMethods;");
  public final DexType typeDescriptorType =
      createStaticallyKnownType("Ljava/lang/invoke/TypeDescriptor;");
  public final DexType iteratorType = createStaticallyKnownType("Ljava/util/Iterator;");
  public final DexType listIteratorType = createStaticallyKnownType("Ljava/util/ListIterator;");
  public final DexType enumerationType = createStaticallyKnownType("Ljava/util/Enumeration;");
  public final DexType serializableType = createStaticallyKnownType("Ljava/io/Serializable;");
  public final DexType externalizableType = createStaticallyKnownType("Ljava/io/Externalizable;");
  public final DexType cloneableType = createStaticallyKnownType("Ljava/lang/Cloneable;");
  public final DexType comparableType = createStaticallyKnownType("Ljava/lang/Comparable;");
  public final DexType stringConcatFactoryType =
      createStaticallyKnownType("Ljava/lang/invoke/StringConcatFactory;");
  public final DexType unsafeType = createStaticallyKnownType("Lsun/misc/Unsafe;");
  public final DexType desugarVarHandleType =
      createStaticallyKnownType(desugarVarHandleDescriptorString);
  public final DexType desugarMethodHandlesLookupType =
      createStaticallyKnownType(desugarMethodHandlesLookupDescriptorString);

  public final ObjectMethodsMembers objectMethodsMembers = new ObjectMethodsMembers();
  public final ServiceLoaderMethods serviceLoaderMethods = new ServiceLoaderMethods();
  public final StringConcatFactoryMembers stringConcatFactoryMembers =
      new StringConcatFactoryMembers();

  private final SyntheticNaming syntheticNaming = new SyntheticNaming();

  public SyntheticNaming getSyntheticNaming() {
    return syntheticNaming;
  }

  public final BiMap<DexType, DexType> primitiveToBoxed = HashBiMap.create(
      ImmutableMap.<DexType, DexType>builder()
          .put(booleanType, boxedBooleanType)
          .put(byteType, boxedByteType)
          .put(charType, boxedCharType)
          .put(shortType, boxedShortType)
          .put(intType, boxedIntType)
          .put(longType, boxedLongType)
          .put(floatType, boxedFloatType)
          .put(doubleType, boxedDoubleType)
          .build());

  public final Map<DexType, DexMethod> unboxPrimitiveMethod =
      ImmutableMap.<DexType, DexMethod>builder()
          .put(boxedBooleanType, createUnboxMethod(booleanType, unboxBooleanMethodName))
          .put(boxedByteType, createUnboxMethod(byteType, unboxByteMethodName))
          .put(boxedCharType, createUnboxMethod(charType, unboxCharMethodName))
          .put(boxedShortType, createUnboxMethod(shortType, unboxShortMethodName))
          .put(boxedIntType, createUnboxMethod(intType, unboxIntMethodName))
          .put(boxedLongType, createUnboxMethod(longType, unboxLongMethodName))
          .put(boxedFloatType, createUnboxMethod(floatType, unboxFloatMethodName))
          .put(boxedDoubleType, createUnboxMethod(doubleType, unboxDoubleMethodName))
          .build();

  private DexMethod createUnboxMethod(DexType primitiveType, DexString unboxMethodName) {
    DexProto proto = createProto(primitiveType);
    return createMethod(primitiveToBoxed.get(primitiveType), proto, unboxMethodName);
  }

  // Works both with the boxed and unboxed type.
  public DexMethod getUnboxPrimitiveMethod(DexType type) {
    DexType boxType = primitiveToBoxed.getOrDefault(type, type);
    DexMethod unboxMethod = unboxPrimitiveMethod.get(boxType);
    if (unboxMethod == null) {
      throw new Unreachable("Invalid primitive type descriptor: " + type);
    }
    return unboxMethod;
  }

  // Works both with the boxed and unboxed type.
  public DexMethod getBoxPrimitiveMethod(DexType type) {
    DexType boxType = primitiveToBoxed.getOrDefault(type, type);
    DexType primitive = getPrimitiveFromBoxed(boxType);
    if (primitive == null) {
      throw new Unreachable("Invalid primitive type descriptor: " + type);
    }
    DexProto proto = createProto(boxType, primitive);
    return createMethod(boxType, proto, valueOfMethodName);
  }

  public BoxUnboxPrimitiveMethodRoundtrip getBoxUnboxPrimitiveMethodRoundtrip(DexType type) {
    if (type.isPrimitiveType()) {
      return new BoxUnboxPrimitiveMethodRoundtrip(
          getBoxPrimitiveMethod(type), getUnboxPrimitiveMethod(type));
    } else if (primitiveToBoxed.containsValue(type)) {
      return new BoxUnboxPrimitiveMethodRoundtrip(
          getUnboxPrimitiveMethod(type), getBoxPrimitiveMethod(type));
    } else {
      return null;
    }
  }

  public static class BoxUnboxPrimitiveMethodRoundtrip {

    private final DexMethod boxIfPrimitiveElseUnbox;
    private final DexMethod unboxIfPrimitiveElseBox;

    public BoxUnboxPrimitiveMethodRoundtrip(
        DexMethod boxIfPrimitiveElseUnbox, DexMethod unboxIfPrimitiveElseBox) {
      this.boxIfPrimitiveElseUnbox = boxIfPrimitiveElseUnbox;
      this.unboxIfPrimitiveElseBox = unboxIfPrimitiveElseBox;
    }

    public DexMethod getBoxIfPrimitiveElseUnbox() {
      return boxIfPrimitiveElseUnbox;
    }

    public DexMethod getUnboxIfPrimitiveElseBox() {
      return unboxIfPrimitiveElseBox;
    }
  }

  public DexType getBoxedForPrimitiveType(DexType primitive) {
    assert primitive.isPrimitiveType();
    return primitiveToBoxed.get(primitive);
  }

  public BoxedPrimitiveMembers getBoxedMembersForPrimitiveOrVoidType(DexType type) {
    assert type.isPrimitiveType() || type.isVoidType();
    switch (type.getDescriptor().getFirstByteAsChar()) {
      case 'B':
        return byteMembers;
      case 'C':
        return charMembers;
      case 'D':
        return doubleMembers;
      case 'F':
        return floatMembers;
      case 'I':
        return integerMembers;
      case 'J':
        return longMembers;
      case 'S':
        return shortMembers;
      case 'V':
        return voidMembers;
      case 'Z':
        return booleanMembers;
      default:
        throw new Unreachable("Unknown type " + type);
    }
  }

  public DexType getPrimitiveFromBoxed(DexType boxedPrimitive) {
    return primitiveToBoxed.inverse().get(boxedPrimitive);
  }

  // Boxed Boxed#valueOf(Primitive), e.g., Boolean Boolean#valueOf(B)
  public Set<DexMethod> boxedValueOfMethods() {
    return primitiveToBoxed.entrySet().stream()
        .map(
            entry -> {
              DexType primitive = entry.getKey();
              DexType boxed = entry.getValue();
              return createMethod(
                  boxed.descriptor,
                  valueOfMethodName,
                  boxed.descriptor,
                  new DexString[] {primitive.descriptor});
            })
        .collect(Collectors.toSet());
  }

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

  public final DexMethod deserializeLambdaMethod =
      createMethod(objectType, deserializeLambdaMethodProto, deserializeLambdaMethodName);

  public Map<DexMethod, int[]> libraryMethodsNonNullParamOrThrow =
      buildLibraryMethodsNonNullParamOrThrow();

  private Map<DexMethod, int[]> buildLibraryMethodsNonNullParamOrThrow() {
    ImmutableMap.Builder<DexMethod, int[]> builder = ImmutableMap.builder();
    for (DexMethod requireNonNullMethod : objectsMethods.requireNonNullMethods()) {
      builder.put(requireNonNullMethod, new int[] {0});
    }
    return builder.build();
  }

  public Set<DexMethod> libraryMethodsReturningReceiver =
      ImmutableSet.<DexMethod>builder()
          .addAll(stringBufferMethods.appendMethods)
          .addAll(stringBuilderMethods.appendMethods)
          .build();

  // Library methods listed here are based on their original implementations. That is, we assume
  // these cannot be overridden.
  public final Set<DexMethod> libraryMethodsReturningNonNull =
      ImmutableSet.<DexMethod>builder()
          .add(
              classMethods.getName,
              classMethods.getSimpleName,
              classMethods.forName,
              objectMembers.getClass,
              objectsMethods.requireNonNull,
              objectsMethods.requireNonNullWithMessage,
              objectsMethods.requireNonNullWithMessageSupplier,
              stringMembers.format,
              stringMembers.formatWithLocale,
              stringMembers.valueOf)
          .addAll(boxedValueOfMethods())
          .addAll(stringBufferMethods.appendMethods)
          .addAll(stringBuilderMethods.appendMethods)
          .build();

  // TODO(b/119596718): More idempotent methods? Any singleton accessors? E.g.,
  // java.util.Calendar#getInstance(...) // 4 variants
  // java.util.Locale#getDefault() // returns JVM default locale.
  // android.os.Looper#myLooper() // returns the associated Looper instance.
  // Note that this set is used for canonicalization of method invocations, together with a set of
  // library methods that do not have side effects.
  public Set<DexMethod> libraryMethodsWithReturnValueDependingOnlyOnArguments =
      ImmutableSet.<DexMethod>builder()
          .addAll(boxedValueOfMethods())
          .build();

  public Set<DexType> libraryTypesAssumedToBePresent =
      ImmutableSet.<DexType>builder()
          .add(
              androidAppActivity,
              callableType,
              enumType,
              npeType,
              objectType,
              stringBufferType,
              stringBuilderType,
              stringType)
          .addAll(primitiveToBoxed.values())
          .build();

  public Set<DexType> libraryClassesWithoutStaticInitialization =
      ImmutableSet.of(
          boxedBooleanType,
          boxedByteType,
          boxedCharType,
          boxedDoubleType,
          boxedFloatType,
          boxedIntType,
          boxedLongType,
          boxedNumberType,
          boxedShortType,
          boxedVoidType,
          enumType,
          javaLangSystemType,
          npeType,
          objectType,
          stringBufferType,
          stringBuilderType,
          stringType);

  private boolean skipNameValidationForTesting = false;

  public void setSkipNameValidationForTesting(boolean skipNameValidationForTesting) {
    this.skipNameValidationForTesting = skipNameValidationForTesting;
  }

  public boolean getSkipNameValidationForTesting() {
    return skipNameValidationForTesting;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isLambdaMetafactoryMethod(DexMethod dexMethod) {
    return dexMethod == metafactoryMethod || dexMethod == metafactoryAltMethod;
  }

  public abstract static class LibraryMembers {

    public void forEachFinalField(Consumer<DexField> consumer) {}
  }

  public abstract static class BoxedPrimitiveMembers extends LibraryMembers {

    public abstract DexField getTypeField();
  }

  public class AndroidOsBuildMembers extends LibraryMembers {

    public final DexField BOOTLOADER = createField(androidOsBuildType, stringType, "BOOTLOADER");
    public final DexField BRAND = createField(androidOsBuildType, stringType, "BRAND");
    public final DexField CPU_ABI = createField(androidOsBuildType, stringType, "CPU_ABI");
    public final DexField CPU_ABI2 = createField(androidOsBuildType, stringType, "CPU_ABI2");
    public final DexField DEVICE = createField(androidOsBuildType, stringType, "DEVICE");
    public final DexField DISPLAY = createField(androidOsBuildType, stringType, "DISPLAY");
    public final DexField FINGERPRINT = createField(androidOsBuildType, stringType, "FINGERPRINT");
    public final DexField HARDWARE = createField(androidOsBuildType, stringType, "HARDWARE");
    public final DexField MANUFACTURER =
        createField(androidOsBuildType, stringType, "MANUFACTURER");
    public final DexField MODEL = createField(androidOsBuildType, stringType, "MODEL");
    public final DexField PRODUCT = createField(androidOsBuildType, stringType, "PRODUCT");
    public final DexField SERIAL = createField(androidOsBuildType, stringType, "SERIAL");
    public final DexField SUPPORTED_32_BIT_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_32_BIT_ABIS");
    public final DexField SUPPORTED_64_BIT_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_64_BIT_ABIS");
    public final DexField SUPPORTED_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_ABIS");
    public final DexField TIME = createField(androidOsBuildType, longType, "TIME");
    public final DexField TYPE = createField(androidOsBuildType, stringType, "TYPE");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(BOOTLOADER);
      consumer.accept(BRAND);
      consumer.accept(CPU_ABI);
      consumer.accept(CPU_ABI2);
      consumer.accept(DEVICE);
      consumer.accept(DISPLAY);
      consumer.accept(FINGERPRINT);
      consumer.accept(HARDWARE);
      consumer.accept(MANUFACTURER);
      consumer.accept(MODEL);
      consumer.accept(PRODUCT);
      consumer.accept(SERIAL);
      consumer.accept(SUPPORTED_32_BIT_ABIS);
      consumer.accept(SUPPORTED_64_BIT_ABIS);
      consumer.accept(SUPPORTED_ABIS);
      consumer.accept(TIME);
      consumer.accept(TYPE);
    }
  }

  public class AndroidOsBuildVersionMembers extends LibraryMembers {

    public final DexField CODENAME = createField(androidOsBuildVersionType, stringType, "CODENAME");
    public final DexField RELEASE = createField(androidOsBuildVersionType, stringType, "RELEASE");
    public final DexField SDK = createField(androidOsBuildVersionType, stringType, "SDK");
    public final DexField SDK_INT = createField(androidOsBuildVersionType, intType, "SDK_INT");
    public final DexField SECURITY_PATCH =
        createField(androidOsBuildVersionType, stringType, "SECURITY_PATCH");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CODENAME);
      consumer.accept(RELEASE);
      consumer.accept(SDK);
      consumer.accept(SDK_INT);
      consumer.accept(SECURITY_PATCH);
    }
  }

  public class AndroidOsBundleMembers extends LibraryMembers {

    public final DexField CREATOR =
        createField(androidOsBundleType, androidOsParcelableCreatorType, "CREATOR");
    public final DexField EMPTY = createField(androidOsBundleType, androidOsBundleType, "EMPTY");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CREATOR);
      consumer.accept(EMPTY);
    }
  }

  public class AndroidSystemOsConstantsMembers extends LibraryMembers {

    public final DexField S_IRUSR = createField(androidSystemOsConstantsType, intType, "S_IRUSR");
    public final DexField S_IXUSR = createField(androidSystemOsConstantsType, intType, "S_IXUSR");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(S_IRUSR);
      consumer.accept(S_IXUSR);
    }
  }

  public class AndroidViewViewMembers extends LibraryMembers {

    public final DexField TRANSLATION_Z =
        createField(androidViewViewType, androidUtilPropertyType, "TRANSLATION_Z");
    public final DexField EMPTY_STATE_SET =
        createField(androidViewViewType, intArrayType, "EMPTY_STATE_SET");
    public final DexField ENABLED_STATE_SET =
        createField(androidViewViewType, intArrayType, "ENABLED_STATE_SET");
    public final DexField PRESSED_ENABLED_STATE_SET =
        createField(androidViewViewType, intArrayType, "PRESSED_ENABLED_STATE_SET");
    public final DexField SELECTED_STATE_SET =
        createField(androidViewViewType, intArrayType, "SELECTED_STATE_SET");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TRANSLATION_Z);
      consumer.accept(EMPTY_STATE_SET);
      consumer.accept(ENABLED_STATE_SET);
      consumer.accept(PRESSED_ENABLED_STATE_SET);
      consumer.accept(SELECTED_STATE_SET);
    }
  }

  // android.util.SparseArray
  public class AndroidUtilSparseArrayMembers extends LibraryMembers {
    public final DexMethod put =
        createMethod(androidUtilSparseArrayType, createProto(voidType, intType, objectType), "put");
    public final DexMethod set =
        createMethod(
            androidUtilSparseArrayType, createProto(voidType, intType, objectType), setString);
  }

  // android.content.res.TypedArray
  public class AndroidContentResTypedArrayMembers extends LibraryMembers {
    public final DexMethod recycle =
        createMethod(androidContentResTypedArrayType, createProto(voidType), "recycle");
    public final DexMethod close =
        createMethod(androidContentResTypedArrayType, createProto(voidType), "close");
  }

  // android.content.ContentProviderClient
  public class AndroidContentContentProviderClientMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidContentContentProviderClientType, createProto(booleanType), "release");
    public final DexMethod close =
        createMethod(androidContentContentProviderClientType, createProto(voidType), "close");
  }

  // android.drm.DrmManagerClient
  public class AndroidDrmDrmManagerClientMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidDrmDrmManagerClientType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(androidDrmDrmManagerClientType, createProto(voidType), "close");
  }

  // android.media.MediaDrm
  public class AndroidMediaMediaDrmMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidMediaMediaDrmType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(androidMediaMediaDrmType, createProto(voidType), "close");
  }

  // android.media.MediaMetadataRetriever
  public class AndroidMediaMetadataRetrieverMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidMediaMediaMetadataRetrieverType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(androidMediaMediaMetadataRetrieverType, createProto(voidType), "close");
  }

  public class BooleanMembers extends BoxedPrimitiveMembers {

    public final DexField FALSE = createField(boxedBooleanType, boxedBooleanType, "FALSE");
    public final DexField TRUE = createField(boxedBooleanType, boxedBooleanType, "TRUE");
    public final DexField TYPE = createField(boxedBooleanType, classType, "TYPE");

    public final DexMethod booleanValue =
        createMethod(boxedBooleanType, createProto(booleanType), "booleanValue");
    public final DexMethod parseBoolean =
        createMethod(boxedBooleanType, createProto(booleanType, stringType), "parseBoolean");
    public final DexMethod valueOf =
        createMethod(boxedBooleanType, createProto(boxedBooleanType, booleanType), "valueOf");
    public final DexMethod toString =
        createMethod(boxedBooleanType, createProto(stringType), "toString");

    private BooleanMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(FALSE);
      consumer.accept(TRUE);
      consumer.accept(TYPE);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class ByteMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedByteType, classType, "TYPE");

    public final DexMethod byteValue =
        createMethod(boxedByteType, createProto(byteType), "byteValue");
    public final DexMethod toString =
        createMethod(boxedByteType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedByteType, createProto(boxedByteType, byteType), "valueOf");

    private ByteMembers() {}

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class CharMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedCharType, classType, "TYPE");

    public final DexMethod charValue =
        createMethod(boxedCharType, createProto(charType), "charValue");
    public final DexMethod toString =
        createMethod(boxedCharType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedCharType, createProto(boxedCharType, charType), "valueOf");

    private CharMembers() {}

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class FloatMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedFloatType, classType, "TYPE");

    public final DexMethod floatValue =
        createMethod(boxedFloatType, createProto(floatType), "floatValue");
    public final DexMethod toString =
        createMethod(boxedFloatType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedFloatType, createProto(boxedFloatType, floatType), "valueOf");

    private FloatMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class JavaIoFileMembers extends LibraryMembers {

    public final DexField pathSeparator = createField(javaIoFileType, stringType, "pathSeparator");
    public final DexField separator = createField(javaIoFileType, stringType, "separator");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(pathSeparator);
      consumer.accept(separator);
    }
  }

  public class JavaMathBigIntegerMembers extends LibraryMembers {

    public final DexField ONE = createField(javaMathBigIntegerType, javaMathBigIntegerType, "ONE");
    public final DexField ZERO =
        createField(javaMathBigIntegerType, javaMathBigIntegerType, "ZERO");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(ONE);
      consumer.accept(ZERO);
    }
  }

  public class JavaNioByteOrderMembers extends LibraryMembers {

    public final DexField LITTLE_ENDIAN =
        createField(javaNioByteOrderType, javaNioByteOrderType, "LITTLE_ENDIAN");
    public final DexField BIG_ENDIAN =
        createField(javaNioByteOrderType, javaNioByteOrderType, "BIG_ENDIAN");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(LITTLE_ENDIAN);
      consumer.accept(BIG_ENDIAN);
    }
  }

  public class JavaUtilArraysMethods {

    public final DexMethod asList;
    public final DexMethod equalsObjectArray;

    private JavaUtilArraysMethods() {
      asList =
          createMethod(
              arraysDescriptor,
              createString("asList"),
              listDescriptor,
              new DexString[] {objectArrayDescriptor});
      equalsObjectArray =
          createMethod(
              arraysDescriptor,
              equalsMethodName,
              booleanDescriptor,
              new DexString[] {objectArrayDescriptor, objectArrayDescriptor});
    }
  }

  public class JavaUtilComparatorMembers extends LibraryMembers {

    public final DexField EMPTY_LIST =
        createField(javaUtilCollectionsType, javaUtilListType, "EMPTY_LIST");
    public final DexField EMPTY_SET =
        createField(javaUtilCollectionsType, javaUtilSetType, "EMPTY_SET");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(EMPTY_LIST);
      consumer.accept(EMPTY_SET);
    }
  }

  public class JavaUtilConcurrentTimeUnitMembers extends LibraryMembers {

    public final DexField DAYS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "DAYS");
    public final DexField HOURS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "HOURS");
    public final DexField MICROSECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MICROSECONDS");
    public final DexField MILLISECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MILLISECONDS");
    public final DexField MINUTES =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MINUTES");
    public final DexField NANOSECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "NANOSECONDS");
    public final DexField SECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "SECONDS");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(DAYS);
      consumer.accept(HOURS);
      consumer.accept(MICROSECONDS);
      consumer.accept(MILLISECONDS);
      consumer.accept(MINUTES);
      consumer.accept(NANOSECONDS);
      consumer.accept(SECONDS);
    }
  }

  public class JavaUtilLocaleMembers extends LibraryMembers {

    public final DexField ENGLISH = createField(javaUtilLocaleType, javaUtilLocaleType, "ENGLISH");
    public final DexField ROOT = createField(javaUtilLocaleType, javaUtilLocaleType, "ROOT");
    public final DexField US = createField(javaUtilLocaleType, javaUtilLocaleType, "US");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(ENGLISH);
      consumer.accept(ROOT);
      consumer.accept(US);
    }
  }

  public class JavaUtilLoggingLevelMembers extends LibraryMembers {

    public final DexField CONFIG =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "CONFIG");
    public final DexField FINE =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINE");
    public final DexField FINER =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINER");
    public final DexField FINEST =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINEST");
    public final DexField SEVERE =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "SEVERE");
    public final DexField WARNING =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "WARNING");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CONFIG);
      consumer.accept(FINE);
      consumer.accept(FINER);
      consumer.accept(FINEST);
      consumer.accept(SEVERE);
      consumer.accept(WARNING);
    }
  }

  public class LongMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedLongType, classType, "TYPE");

    public final DexMethod compare =
        createMethod(boxedLongType, createProto(intType, longType, longType), "compare");
    public final DexMethod longValue =
        createMethod(boxedLongType, createProto(longType), "longValue");
    public final DexMethod toString =
        createMethod(boxedLongType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedLongType, createProto(boxedLongType, longType), "valueOf");

    private LongMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class DoubleMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedDoubleType, classType, "TYPE");

    public final DexMethod doubleValue =
        createMethod(boxedDoubleType, createProto(doubleType), "doubleValue");
    public final DexMethod isNaN =
        createMethod(boxedDoubleType, createProto(booleanType, doubleType), "isNaN");
    public final DexMethod toString =
        createMethod(boxedDoubleType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedDoubleType, createProto(boxedDoubleType, doubleType), "valueOf");

    private DoubleMembers() {}

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class IntegerMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedIntType, classType, "TYPE");

    public final DexMethod intValue = createMethod(boxedIntType, createProto(intType), "intValue");
    public final DexMethod toString =
        createMethod(boxedIntType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedIntType, createProto(boxedIntType, intType), "valueOf");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class StringConcatFactoryMembers {

    public final DexMethod makeConcat =
        createMethod(
            stringConcatFactoryType,
            createProto(callSiteType, lookupType, stringType, methodTypeType),
            createString("makeConcat"));
    public final DexMethod makeConcatWithConstants =
        createMethod(
            stringConcatFactoryType,
            createProto(
                callSiteType, lookupType, stringType, methodTypeType, stringType, objectArrayType),
            createString("makeConcatWithConstants"));
  }

  public class VoidMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(voidType, classType, "TYPE");

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class ThrowableMethods {

    public final DexMethod addSuppressed;
    public final DexMethod getMessage;
    public final DexMethod getSuppressed;
    public final DexMethod initCause;

    private ThrowableMethods() {
      addSuppressed = createMethod(throwableDescriptor,
          createString("addSuppressed"), voidDescriptor, new DexString[]{throwableDescriptor});
      getSuppressed = createMethod(throwableDescriptor,
          createString("getSuppressed"), throwableArrayDescriptor, DexString.EMPTY_ARRAY);
      initCause = createMethod(throwableDescriptor, createString("initCause"), throwableDescriptor,
          new DexString[] { throwableDescriptor });
      getMessage =
          createMethod(
              throwableDescriptor,
              createString("getMessage"),
              stringDescriptor,
              DexString.EMPTY_ARRAY);
    }
  }

  public class AssertionErrorMethods {
    public final DexMethod initMessage;
    public final DexMethod initMessageAndCause;

    private AssertionErrorMethods() {
      this.initMessage =
          createMethod(assertionErrorDescriptor, constructorMethodName, voidDescriptor,
              new DexString[] { objectDescriptor });
      this.initMessageAndCause =
          createMethod(assertionErrorDescriptor, constructorMethodName, voidDescriptor,
              new DexString[] { stringDescriptor, throwableDescriptor });
    }
  }

  public class RecordMembers {
    public final DexMethod constructor = createMethod(recordType, createProto(voidType), "<init>");
    public final DexMethod equals =
        createMethod(recordType, createProto(booleanType, objectType), "equals");
    public final DexMethod hashCode = createMethod(recordType, createProto(intType), "hashCode");
    public final DexMethod toString = createMethod(recordType, createProto(stringType), "toString");
  }

  public class ObjectMethodsMembers {
    public final DexMethod bootstrap =
        createMethod(
            objectMethodsType,
            createProto(
                objectType,
                lookupType,
                stringType,
                typeDescriptorType,
                classType,
                stringType,
                createArrayType(1, methodHandleType)),
            "bootstrap");
  }

  public class ObjectMembers {

    /**
     * This field is not on {@link Object}, but will be synthesized on program classes as a static
     * field, for the compiler to have a principled way to trigger the initialization of a given
     * class.
     */
    public final DexField clinitField = createField(objectType, intType, "$r8$clinit");

    public final DexMethod clone;
    public final DexMethod equals =
        createMethod(objectType, createProto(booleanType, objectType), "equals");
    public final DexMethod getClass;
    public final DexMethod hashCode = createMethod(objectType, createProto(intType), "hashCode");
    public final DexMethod constructor;
    public final DexMethod finalize;
    public final DexMethod toString;
    public final DexMethod notify;
    public final DexMethod notifyAll;
    public final DexMethod wait;
    public final DexMethod waitLong;
    public final DexMethod waitLongInt;

    private ObjectMembers() {
      // The clone method is installed on each array, so one has to use method.match(clone).
      clone = createMethod(objectType, createProto(objectType), cloneMethodName);
      getClass = createMethod(objectDescriptor,
          getClassMethodName, classDescriptor, DexString.EMPTY_ARRAY);
      constructor = createMethod(objectDescriptor,
          constructorMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
      finalize = createMethod(objectDescriptor,
          finalizeMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
      toString = createMethod(objectDescriptor,
          toStringMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
      notify =
          createMethod(objectDescriptor, notifyMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      notifyAll =
          createMethod(
              objectDescriptor, notifyAllMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      wait = createMethod(objectDescriptor, waitMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      waitLong =
          createMethod(
              objectDescriptor, waitMethodName, voidDescriptor, new DexString[] {longDescriptor});
      waitLongInt =
          createMethod(
              objectDescriptor,
              waitMethodName,
              voidDescriptor,
              new DexString[] {longDescriptor, intDescriptor});
    }

    public boolean isObjectMember(DexMethod method) {
      return method.match(clone)
          || method.match(getClass)
          || method.match(constructor)
          || method.match(finalize)
          || method.match(toString)
          || method.match(hashCode)
          || method.match(equals)
          || method.match(notify)
          || method.match(notifyAll)
          || method.match(wait)
          || method.match(waitLong)
          || method.match(waitLongInt);
    }

    public DexMethod matchingPublicObjectMember(DexMethod method) {
      switch (method.getName().byteAt(0)) {
        case 't':
          if (method.match(toString)) {
            return toString;
          }
          break;
        case 'h':
          if (method.match(hashCode)) {
            return hashCode;
          }
          break;
        case 'e':
          if (method.match(equals)) {
            return equals;
          }
          break;
        case 'g':
          if (method.match(getClass)) {
            return getClass;
          }
          break;
        case 'n':
          if (method.match(notify)) {
            return notify;
          }
          if (method.match(notifyAll)) {
            return notifyAll;
          }
          break;
        case 'w':
          if (method.match(wait)) {
            return wait;
          }
          if (method.match(waitLong)) {
            return waitLong;
          }
          if (method.match(waitLongInt)) {
            return waitLongInt;
          }
          break;
        default:
          // Methods finalize and clone are not public.
          return null;
      }
      return null;
    }
  }

  public class BufferMembers {
    public final DexMethod positionArg =
        createMethod(bufferType, createProto(bufferType, intType), "position");
    public final DexMethod limitArg =
        createMethod(bufferType, createProto(bufferType, intType), "limit");
    public final DexMethod mark = createMethod(bufferType, createProto(bufferType), "mark");
    public final DexMethod reset = createMethod(bufferType, createProto(bufferType), "reset");
    public final DexMethod clear = createMethod(bufferType, createProto(bufferType), "clear");
    public final DexMethod flip = createMethod(bufferType, createProto(bufferType), "flip");
    public final DexMethod rewind = createMethod(bufferType, createProto(bufferType), "rewind");
    public final List<DexMethod> bufferCovariantMethods =
        ImmutableList.of(positionArg, limitArg, mark, reset, clear, flip, rewind);
  }

  public class ObjectsMethods {

    public final DexMethod equals =
        createMethod(objectsType, createProto(booleanType, objectType, objectType), "equals");
    public final DexMethod hashCode =
        createMethod(objectsType, createProto(intType, objectType), "hashCode");
    public final DexMethod isNull =
        createMethod(objectsType, createProto(booleanType, objectType), "isNull");
    public final DexMethod nonNull =
        createMethod(objectsType, createProto(booleanType, objectType), "nonNull");
    public final DexMethod requireNonNull;
    public final DexMethod requireNonNullWithMessage;
    public final DexMethod requireNonNullWithMessageSupplier;
    public final DexMethod requireNonNullElse =
        createMethod(
            objectsType, createProto(objectType, objectType, objectType), "requireNonNullElse");
    public final DexMethod requireNonNullElseGet =
        createMethod(
            objectsType,
            createProto(objectType, objectType, supplierType),
            "requireNonNullElseGet");
    public final DexMethod toStringWithObject =
        createMethod(objectsType, createProto(stringType, objectType), "toString");
    public final DexMethod toStringWithObjectAndNullDefault =
        createMethod(objectsType, createProto(stringType, objectType, stringType), "toString");

    private ObjectsMethods() {
      DexString requireNonNullMethodName = createString("requireNonNull");
      requireNonNull =
          createMethod(objectsType, createProto(objectType, objectType), requireNonNullMethodName);
      requireNonNullWithMessage =
          createMethod(
              objectsType,
              createProto(objectType, objectType, stringType),
              requireNonNullMethodName);
      requireNonNullWithMessageSupplier =
          createMethod(
              objectsType,
              createProto(objectType, objectType, supplierType),
              requireNonNullMethodName);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isRequireNonNullMethod(DexMethod method) {
      return method == requireNonNull
          || method == requireNonNullWithMessage
          || method == requireNonNullWithMessageSupplier
          || method == requireNonNullElse
          || method == requireNonNullElseGet;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isToStringMethod(DexMethod method) {
      return method == toStringWithObject || method == toStringWithObjectAndNullDefault;
    }

    public Iterable<DexMethod> requireNonNullMethods() {
      return ImmutableList.of(
          requireNonNull, requireNonNullWithMessage, requireNonNullWithMessageSupplier);
    }
  }

  public class ClassMethods {

    public final DexMethod desiredAssertionStatus;
    public final DexMethod forName;
    public final DexMethod forName3;
    public final DexMethod getClassLoader =
        createMethod(classType, createProto(classLoaderType), "getClassLoader");
    public final DexMethod getName;
    public final DexMethod getCanonicalName;
    public final DexMethod getSimpleName;
    public final DexMethod getTypeName;
    public final DexMethod getConstructor;
    public final DexMethod getDeclaredConstructor;
    public final DexMethod getField;
    public final DexMethod getDeclaredField;
    public final DexMethod getMethod;
    public final DexMethod getDeclaredMethod;
    public final DexMethod getPackage =
        createMethod(classType, createProto(packageType), "getPackage");
    public final DexMethod newInstance;
    private final Set<DexMethod> getMembers;
    public final Set<DexMethod> getNames;

    private ClassMethods() {
      desiredAssertionStatus = createMethod(classDescriptor,
          desiredAssertionStatusMethodName, booleanDescriptor, DexString.EMPTY_ARRAY);
      forName =
          createMethod(
              classDescriptor,
              forNameMethodName,
              classDescriptor,
              new DexString[] {stringDescriptor});
      forName3 =
          createMethod(
              classDescriptor,
              forNameMethodName,
              classDescriptor,
              new DexString[] {stringDescriptor, booleanDescriptor, classLoaderDescriptor});
      getName = createMethod(classDescriptor, getNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getCanonicalName = createMethod(
          classDescriptor, getCanonicalNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getSimpleName = createMethod(
          classDescriptor, getSimpleNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getTypeName = createMethod(
          classDescriptor, getTypeNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getConstructor =
          createMethod(classType, createProto(constructorType, classArrayType), "getConstructor");
      getDeclaredConstructor =
          createMethod(
              classDescriptor,
              getDeclaredConstructorName,
              constructorDescriptor,
              new DexString[] {classArrayDescriptor});
      getField = createMethod(classDescriptor, getFieldName, fieldDescriptor,
          new DexString[] {stringDescriptor});
      getDeclaredField = createMethod(classDescriptor, getDeclaredFieldName, fieldDescriptor,
          new DexString[] {stringDescriptor});
      getMethod = createMethod(classDescriptor, getMethodName, methodDescriptor,
          new DexString[] {stringDescriptor, classArrayDescriptor});
      getDeclaredMethod = createMethod(classDescriptor, getDeclaredMethodName, methodDescriptor,
          new DexString[] {stringDescriptor, classArrayDescriptor});
      newInstance =
          createMethod(classDescriptor, newInstanceName, objectDescriptor, DexString.EMPTY_ARRAY);
      getMembers = ImmutableSet.of(getField, getDeclaredField, getMethod, getDeclaredMethod);
      getNames = ImmutableSet.of(getName, getCanonicalName, getSimpleName, getTypeName);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isReflectiveClassLookup(DexMethod method) {
      return method == forName || method == forName3;
    }

    public boolean isReflectiveMemberLookup(DexMethod method) {
      return getMembers.contains(method);
    }

    public boolean isReflectiveNameLookup(DexMethod method) {
      return getNames.contains(method);
    }
  }

  public class ConstructorMethods {

    public final DexMethod newInstance;

    private ConstructorMethods() {
      newInstance =
          createMethod(
              constructorDescriptor,
              newInstanceName,
              objectDescriptor,
              new DexString[] {objectArrayDescriptor});
    }
  }

  public class AndroidUtilLogMembers {

    public final DexMethod i =
        createMethod(androidUtilLogType, createProto(intType, stringType, stringType), "i");

    private AndroidUtilLogMembers() {}
  }

  public class JavaLangAnnotationRetentionPolicyMembers {

    public final DexField CLASS =
        createField(
            javaLangAnnotationRetentionPolicyType, javaLangAnnotationRetentionPolicyType, "CLASS");

    private JavaLangAnnotationRetentionPolicyMembers() {}
  }

  public class JavaLangReflectArrayMembers {

    public final DexMethod newInstanceMethodWithDimensions =
        createMethod(
            javaLangReflectArrayType,
            createProto(objectType, classType, intArrayType),
            "newInstance");

    private JavaLangReflectArrayMembers() {}
  }

  public class JavaLangSystemMembers {

    public final DexField out = createField(javaLangSystemType, javaIoPrintStreamType, "out");

    public final DexMethod arraycopy =
        createMethod(
            javaLangSystemType,
            createProto(voidType, objectType, intType, objectType, intType, intType),
            "arraycopy");
    public final DexMethod identityHashCode =
        createMethod(javaLangSystemType, createProto(intType, objectType), identityHashCodeName);

    private JavaLangSystemMembers() {}
  }

  public class JavaIoPrintStreamMembers {

    public final DexMethod printlnWithString =
        createMethod(javaIoPrintStreamType, createProto(voidType, stringType), "println");

    private JavaIoPrintStreamMembers() {}
  }

  public class EnumMembers {

    public final DexField nameField = createField(enumType, stringType, "name");
    public final DexField ordinalField = createField(enumType, intType, "ordinal");

    public final DexMethod valueOf;
    public final DexMethod ordinalMethod;
    public final DexMethod nameMethod;
    public final DexMethod toString;
    public final DexMethod compareTo;
    public final DexMethod compareToWithObject =
        createMethod(enumType, createProto(intType, objectType), "compareTo");
    public final DexMethod equals;
    public final DexMethod hashCode;

    public final DexMethod constructor =
        createMethod(enumType, createProto(voidType, stringType, intType), constructorMethodName);
    public final DexMethod finalize =
        createMethod(enumType, createProto(voidType), finalizeMethodName);

    private EnumMembers() {
      valueOf =
          createMethod(
              enumDescriptor,
              valueOfMethodName,
              enumDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
      ordinalMethod =
          createMethod(enumDescriptor, ordinalMethodName, intDescriptor, DexString.EMPTY_ARRAY);
      nameMethod =
          createMethod(enumDescriptor, nameMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
      toString =
          createMethod(
              enumDescriptor,
              toStringMethodName,
              stringDescriptor,
              DexString.EMPTY_ARRAY);
      compareTo =
          createMethod(
              enumDescriptor, compareToMethodName, intDescriptor, new DexString[] {enumDescriptor});
      equals =
          createMethod(
              enumDescriptor,
              equalsMethodName,
              booleanDescriptor,
              new DexString[] {objectDescriptor});
      hashCode =
          createMethod(enumDescriptor, hashCodeMethodName, intDescriptor, DexString.EMPTY_ARRAY);
    }

    public void forEachField(Consumer<DexField> fn) {
      fn.accept(nameField);
      fn.accept(ordinalField);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isNameOrOrdinalField(DexField field) {
      return field == nameField || field == ordinalField;
    }

    public boolean isEnumFieldCandidate(DexClassAndField staticField) {
      FieldAccessFlags accessFlags = staticField.getAccessFlags();
      assert accessFlags.isStatic();
      return accessFlags.isEnum() && accessFlags.isFinal();
    }

    @SuppressWarnings("ReferenceEquality")
    // In some case, the enum field may be respecialized to an enum subtype. In this case, one
    // can pass the encoded field as well as the field with the super enum type for the checks.
    public boolean isEnumField(
        DexClassAndField staticField, DexType enumType, Set<DexType> subtypes) {
      assert staticField.getAccessFlags().isStatic();
      return (staticField.getType() == enumType || subtypes.contains(staticField.getType()))
          && isEnumFieldCandidate(staticField);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isValuesFieldCandidate(DexClassAndField staticField, DexType enumType) {
      FieldAccessFlags accessFlags = staticField.getAccessFlags();
      assert accessFlags.isStatic();
      return staticField.getType().isArrayType()
          && staticField.getType().toArrayElementType(DexItemFactory.this) == enumType
          && accessFlags.isSynthetic()
          && accessFlags.isFinal();
    }
  }

  public class NullPointerExceptionMethods {

    public final DexMethod init =
        createMethod(npeType, createProto(voidType), constructorMethodName);
    public final DexMethod initWithMessage =
        createMethod(npeType, createProto(voidType, stringType), constructorMethodName);
  }

  public class IllegalArgumentExceptionMethods {

    public final DexMethod initWithMessage =
        createMethod(
            illegalArgumentExceptionType, createProto(voidType, stringType), constructorMethodName);
  }

  /**
   * All boxed types (Boolean, Byte, ...) have a field named TYPE which contains the Class object
   * for the primitive type.
   *
   * E.g. for Boolean https://docs.oracle.com/javase/8/docs/api/java/lang/Boolean.html#TYPE.
   */
  public class PrimitiveTypesBoxedTypeFields {

    public final DexField byteTYPE;
    public final DexField charTYPE;
    public final DexField shortTYPE;
    public final DexField intTYPE;
    public final DexField longTYPE;
    public final DexField floatTYPE;
    public final DexField doubleTYPE;

    private final Map<DexField, DexType> boxedFieldTypeToPrimitiveType;

    private PrimitiveTypesBoxedTypeFields() {
      byteTYPE = createField(boxedByteType, classType, "TYPE");
      charTYPE = createField(boxedCharType, classType, "TYPE");
      shortTYPE = createField(boxedShortType, classType, "TYPE");
      intTYPE = createField(boxedIntType, classType, "TYPE");
      longTYPE = createField(boxedLongType, classType, "TYPE");
      floatTYPE = createField(boxedFloatType, classType, "TYPE");
      doubleTYPE = createField(boxedDoubleType, classType, "TYPE");

      boxedFieldTypeToPrimitiveType =
          ImmutableMap.<DexField, DexType>builder()
              .put(booleanMembers.TYPE, booleanType)
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
              new DexString[] {classDescriptor, stringDescriptor});
      longUpdater =
          createMethod(
              longFieldUpdaterDescriptor,
              newUpdaterName,
              longFieldUpdaterDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
      referenceUpdater =
          createMethod(
              referenceFieldUpdaterDescriptor,
              newUpdaterName,
              referenceFieldUpdaterDescriptor,
              new DexString[] {classDescriptor, classDescriptor, stringDescriptor});
      updaters = ImmutableSet.of(intUpdater, longUpdater, referenceUpdater);
    }

    public boolean isFieldUpdater(DexMethod method) {
      return updaters.contains(method);
    }
  }

  public class ShortMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedShortType, classType, "TYPE");

    public final DexMethod shortValue =
        createMethod(boxedShortType, createProto(shortType), "shortValue");
    public final DexMethod toString =
        createMethod(boxedShortType, createProto(stringType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedShortType, createProto(boxedShortType, shortType), "valueOf");

    private ShortMembers() {}

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class StringMembers extends LibraryMembers {

    public final DexField CASE_INSENSITIVE_ORDER =
        createField(stringType, javaUtilComparatorType, "CASE_INSENSITIVE_ORDER");

    public final DexMethod isEmpty;
    public final DexMethod length;

    public final DexMethod concat;
    public final DexMethod constructor =
        createMethod(stringType, createProto(voidType, stringType), constructorMethodName);
    public final DexMethod contains;
    public final DexMethod startsWith;
    public final DexMethod endsWith;
    public final DexMethod equals;
    public final DexMethod equalsIgnoreCase;
    public final DexMethod contentEqualsCharSequence;

    public final DexMethod indexOfInt;
    public final DexMethod indexOfString;
    public final DexMethod lastIndexOfInt;
    public final DexMethod lastIndexOfString;
    public final DexMethod compareTo;
    public final DexMethod compareToIgnoreCase;

    public final DexMethod hashCode;

    public final DexMethod format;
    public final DexMethod formatWithLocale;
    public final DexMethod valueOf;
    public final DexMethod toString;
    public final DexMethod intern;

    public final DexMethod trim = createMethod(stringType, createProto(stringType), trimName);

    private StringMembers() {
      isEmpty = createMethod(
          stringDescriptor, isEmptyMethodName, booleanDescriptor, DexString.EMPTY_ARRAY);
      length = createMethod(
          stringDescriptor, lengthMethodName, intDescriptor, DexString.EMPTY_ARRAY);

      DexString[] needsOneCharSequence = { charSequenceDescriptor };
      DexString[] needsOneString = { stringDescriptor };
      DexString[] needsOneObject = { objectDescriptor };
      DexString[] needsOneInt = { intDescriptor };

      concat = createMethod(stringDescriptor, concatMethodName, stringDescriptor, needsOneString);
      contains = createMethod(
          stringDescriptor, containsMethodName, booleanDescriptor, needsOneCharSequence);
      startsWith = createMethod(
          stringDescriptor, startsWithMethodName, booleanDescriptor, needsOneString);
      endsWith = createMethod(
          stringDescriptor, endsWithMethodName, booleanDescriptor, needsOneString);
      equals = createMethod(
          stringDescriptor, equalsMethodName, booleanDescriptor, needsOneObject);
      equalsIgnoreCase = createMethod(
          stringDescriptor, equalsIgnoreCaseMethodName, booleanDescriptor, needsOneString);
      contentEqualsCharSequence = createMethod(
          stringDescriptor, contentEqualsMethodName, booleanDescriptor, needsOneCharSequence);

      indexOfString =
          createMethod(stringDescriptor, indexOfMethodName, intDescriptor, needsOneString);
      indexOfInt =
          createMethod(stringDescriptor, indexOfMethodName, intDescriptor, needsOneInt);
      lastIndexOfString =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, needsOneString);
      lastIndexOfInt =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, needsOneInt);
      compareTo =
          createMethod(stringDescriptor, compareToMethodName, intDescriptor, needsOneString);
      compareToIgnoreCase =
          createMethod(stringDescriptor, compareToIgnoreCaseMethodName, intDescriptor,
              needsOneString);

      hashCode = createMethod(stringType, createProto(intType), hashCodeMethodName);
      format =
          createMethod(
              stringDescriptor,
              formatMethodName,
              stringDescriptor,
              new DexString[] {stringDescriptor, objectArrayDescriptor});
      formatWithLocale =
          createMethod(
              stringDescriptor,
              formatMethodName,
              stringDescriptor,
              new DexString[] {localeDescriptor, stringDescriptor, objectArrayDescriptor});

      valueOf = createMethod(
          stringDescriptor, valueOfMethodName, stringDescriptor, needsOneObject);
      toString = createMethod(
          stringDescriptor, toStringMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
      intern = createMethod(
          stringDescriptor, internMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
    }

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CASE_INSENSITIVE_ORDER);
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
    public final DexMethod capacity;
    public final DexMethod charSequenceConstructor;
    public final DexMethod defaultConstructor;
    public final DexMethod intConstructor;
    public final DexMethod stringConstructor;
    public final DexMethod toString;

    private final Set<DexMethod> appendMethods;
    private final Set<DexMethod> appendPrimitiveMethods;

    private StringBuildingMethods(DexType receiver) {
      DexString append = createString("append");
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
      appendStringBuffer = createMethod(receiver, createProto(receiver, stringBufferType), append);
      capacity = createMethod(receiver, createProto(intType), createString("capacity"));
      charSequenceConstructor =
          createMethod(receiver, createProto(voidType, charSequenceType), constructorMethodName);
      defaultConstructor = createMethod(receiver, createProto(voidType), constructorMethodName);
      intConstructor =
          createMethod(receiver, createProto(voidType, intType), constructorMethodName);
      stringConstructor =
          createMethod(receiver, createProto(voidType, stringType), constructorMethodName);
      toString = createMethod(receiver, createProto(stringType), toStringMethodName);

      appendMethods =
          ImmutableSet.of(
              appendBoolean,
              appendChar,
              appendCharArray,
              appendSubCharArray,
              appendCharSequence,
              appendSubCharSequence,
              appendInt,
              appendDouble,
              appendFloat,
              appendLong,
              appendObject,
              appendString,
              appendStringBuffer);
      appendPrimitiveMethods =
          ImmutableSet.of(
              appendBoolean, appendChar, appendInt, appendDouble, appendFloat, appendLong);
      constructorMethods =
          ImmutableSet.of(
              charSequenceConstructor, defaultConstructor, intConstructor, stringConstructor);
    }

    public final Set<DexMethod> constructorMethods;

    public boolean isAppendMethod(DexMethod method) {
      return appendMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendObjectMethod(DexMethod method) {
      return method == appendObject;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendCharSequenceMethod(DexMethod method) {
      return method == appendCharSequence || method == appendSubCharSequence;
    }

    public boolean isAppendObjectOrCharSequenceMethod(DexMethod method) {
      return isAppendObjectMethod(method) || isAppendCharSequenceMethod(method);
    }

    public boolean isAppendPrimitiveMethod(DexMethod method) {
      return appendPrimitiveMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendSubArrayMethod(DexMethod method) {
      return appendSubCharArray == method || appendSubCharSequence == method;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendStringMethod(DexMethod method) {
      return method == appendString;
    }

    public boolean isConstructorMethod(DexMethod method) {
      return constructorMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean constructorInvokeIsSideEffectFree(
        DexMethod invokedMethod, List<Value> arguments) {
      if (invokedMethod == defaultConstructor) {
        return true;
      }

      if (invokedMethod == charSequenceConstructor) {
        // Performs callbacks on the given CharSequence, which may have side effects.
        TypeElement charSequenceType = arguments.get(1).getType();
        return charSequenceType.isClassType()
            && charSequenceType.asClassType().getClassType() == stringType;
      }

      if (invokedMethod == intConstructor) {
        // NegativeArraySizeException - if the capacity argument is less than 0.
        Value capacityValue = arguments.get(1);
        if (capacityValue.hasValueRange()) {
          return capacityValue.getValueRange().getMin() >= 0;
        }
        return false;
      }

      if (invokedMethod == stringConstructor) {
        // NullPointerException - if str is null.
        Value strValue = arguments.get(1);
        return !strValue.getType().isNullable();
      }

      assert false : "Unexpected invoke targeting `" + invokedMethod.toSourceString() +  "`";
      return false;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendCharArrayMethod(DexMethod method) {
      return method == appendCharArray || method == appendSubCharArray;
    }
  }

  public class SupplierMembers extends LibraryMembers {

    public final DexMethod get = createMethod(supplierType, createProto(objectType), getString);

    private SupplierMembers() {}
  }

  public class PolymorphicMethods {

    private final DexProto signature = createProto(objectType, objectArrayType);
    private final DexProto setSignature = createProto(voidType, objectArrayType);
    private final DexProto compareAndSetSignature = createProto(booleanType, objectArrayType);

    public final Set<DexString> varHandleMethodsWithPolymorphicReturnType =
        createStrings(
            "compareAndExchange",
            "compareAndExchangeAcquire",
            "compareAndExchangeRelease",
            getString,
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
        createStrings(setString, "setOpaque", setReleaseString, setVolatileString);

    public final Set<DexString> varHandleCompareAndSetMethodNames =
        createStrings(
            compareAndSetString,
            weakCompareAndSetString,
            "weakCompareAndSetAcquire",
            "weakCompareAndSetPlain",
            "weakCompareAndSetRelease");

    @SuppressWarnings("ReferenceEquality")
    public DexMethod canonicalize(DexMethod invokeProto) {
      DexMethod result = null;
      if (invokeProto.holder == methodHandleType) {
        if (invokeProto.name == invokeMethodName || invokeProto.name == invokeExactMethodName) {
          result = createMethod(methodHandleType, signature, invokeProto.name);
        }
      } else if (invokeProto.holder == varHandleType) {
        if (varHandleMethodsWithPolymorphicReturnType.contains(invokeProto.name)) {
          result = createMethod(varHandleType, signature, invokeProto.name);
        } else if (varHandleSetMethods.contains(invokeProto.name)) {
          result = createMethod(varHandleType, setSignature, invokeProto.name);
        } else if (varHandleCompareAndSetMethodNames.contains(invokeProto.name)) {
          result = createMethod(varHandleType, compareAndSetSignature, invokeProto.name);
        }
      }
      assert (result != null) == isPolymorphicInvoke(invokeProto);
      return result;
    }

    private Set<DexString> createStrings(Object... strings) {
      IdentityHashMap<DexString, DexString> map = new IdentityHashMap<>();
      for (Object string : strings) {
        DexString dexString =
            string instanceof String ? createString((String) string) : (DexString) string;
        map.put(dexString, dexString);
      }
      return map.keySet();
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isPolymorphicInvoke(DexMethod invokeProto) {
      if (invokeProto.holder == methodHandleType) {
        return invokeProto.name == invokeMethodName || invokeProto.name == invokeExactMethodName;
      }
      if (invokeProto.holder == varHandleType) {
        return varHandleMethodsWithPolymorphicReturnType.contains(invokeProto.name)
            || varHandleSetMethods.contains(invokeProto.name)
            || varHandleCompareAndSetMethodNames.contains(invokeProto.name);
      }
      return false;
    }
  }

  public class ProxyMethods {

    public final DexMethod newProxyInstance;

    private ProxyMethods() {
      newProxyInstance =
          createMethod(
              proxyType,
              createProto(objectType, classLoaderType, classArrayType, invocationHandlerType),
              createString("newProxyInstance"));
    }
  }

  public class ServiceLoaderMethods {

    public final DexMethod load;
    public final DexMethod loadWithClassLoader;
    public final DexMethod loadInstalled;
    public final DexMethod iterator;

    private ServiceLoaderMethods() {
      DexString loadName = createString("load");
      load = createMethod(serviceLoaderType, createProto(serviceLoaderType, classType), loadName);
      loadWithClassLoader =
          createMethod(
              serviceLoaderType,
              createProto(serviceLoaderType, classType, classLoaderType),
              loadName);
      loadInstalled =
          createMethod(
              serviceLoaderType,
              createProto(serviceLoaderType, classType),
              createString("loadInstalled"));
      iterator =
          createMethod(serviceLoaderType, createProto(iteratorType), createString("iterator"));
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isLoadMethod(DexMethod method) {
      return method == load || method == loadWithClassLoader || method == loadInstalled;
    }
  }

  private static <T extends DexItem> T canonicalize(Map<T, T> map, T item) {
    assert item != null;
    assert !DexItemFactory.isInternalSentinel(item);
    T previous = map.putIfAbsent(item, item);
    return previous == null ? item : previous;
  }

  public DexString createMarkerString(int size, byte[] content) {
    DexString potentialMarker = createString(size, content);
    if (Marker.hasMarkerPrefix(potentialMarker.content)) {
      markers.put(potentialMarker, potentialMarker);
    }
    return potentialMarker;
  }

  public DexString createMarkerString(String marker) {
    DexString potentialMarker = createString(marker);
    if (Marker.hasMarkerPrefix(potentialMarker.content)) {
      markers.put(potentialMarker, potentialMarker);
    }
    return potentialMarker;
  }

  public DexString createString(int size, byte[] content) {
    assert !sorted;
    return canonicalize(strings, new DexString(size, content));
  }

  public DexString createString(String source) {
    assert !sorted;
    return canonicalize(strings, new DexString(source));
  }

  public static String escapeMemberString(String str) {
    return str.replace('.', '$');
  }

  public String createMemberString(String baseName, DexType holder, int index) {
    StringBuilder sb = new StringBuilder().append(baseName);
    if (holder != null) {
      sb.append('$').append(escapeMemberString(holder.toSourceString()));
    }

    if (index > 0) {
      sb.append("$").append(index);
    }

    return sb.toString();
  }

  public <T> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName, DexType holder) {
    return createFreshMember(tryString, baseName, holder, 0);
  }

  /**
   * Find a fresh method name that is not used by any other method. The method name takes the form
   * "basename$holdername" or "basename$holdername$index".
   *
   * @param tryString callback to check if the method name is in use.
   */
  public <T> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName, DexType holder, int index) {
    int offset = 0;
    while (true) {
      assert offset < 1000;
      DexString name = createString(createMemberString(baseName, holder, index + offset));
      Optional<T> result = tryString.apply(name);
      if (result.isPresent()) {
        return result.get();
      }
      offset++;
    }
  }

  /**
   * Find a fresh method name that is not used by any other method. The method name takes the form
   * "basename" or "basename$index".
   *
   * @param tryString callback to check if the method name is in use.
   */
  public <T extends DexMember<?, ?>> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName) {
    return createFreshMember(tryString, baseName, null);
  }

  /**
   * Find a fresh method name that is not in the string pool. The name takes the form
   * "basename$holdername" or "basename$holdername$index".
   */
  public DexString createGloballyFreshMemberString(String baseName, DexType holder) {
    assert !sorted;
    int index = 0;
    while (true) {
      String name = createMemberString(baseName, holder, index++);
      DexString dexName = lookupString(name);
      if (dexName == null) {
        return createString(name);
      }
    }
  }

  /**
   * Find a fresh method name that is not in the string pool. The name takes the form "basename" or
   * "basename$index".
   */
  public DexString createGloballyFreshMemberString(String baseName) {
    return createGloballyFreshMemberString(baseName, null);
  }

  public DexType createFreshTypeName(DexType type, Predicate<DexType> isFresh) {
    return createFreshTypeName(type, isFresh, 0);
  }

  public DexType createFreshTypeName(DexType type, Predicate<DexType> isFresh, int index) {
    while (true) {
      DexType newType = type.addSuffixId(index++, this);
      if (isFresh.test(newType)) {
        return newType;
      }
    }
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form
   * baseName$holder$n, where {@code baseName} and {@code holder} are supplied by the user, and
   * {@code n} is picked to be the first number so that {@code isFresh.apply(method)} returns {@code
   * true}.
   *
   * @param holder indicates where the method originates from.
   */
  public DexMethod createFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh) {
    assert holder != null;
    return internalCreateFreshMethodNameWithHolder(baseName, holder, proto, target, isFresh);
  }

  public DexMethod createFreshMethodNameWithoutHolder(
      String baseName, DexProto proto, DexType target, Predicate<DexMethod> isFresh) {
    return createFreshMethodNameWithoutHolder(baseName, proto, target, isFresh, 0);
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form baseName$n,
   * where {@code baseName} is supplied by the user, and {@code n} is picked to be the first number
   * starting from {@param index} so that {@code isFresh.apply(method)} returns {@code true}.
   */
  public DexMethod createFreshMethodNameWithoutHolder(
      String baseName, DexProto proto, DexType target, Predicate<DexMethod> isFresh, int index) {
    return internalCreateFreshMethodNameWithHolder(baseName, null, proto, target, isFresh, index);
  }

  private DexMethod internalCreateFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh) {
    return internalCreateFreshMethodNameWithHolder(baseName, holder, proto, target, isFresh, 0);
  }

  /**
   * Used to find a fresh method name of the from {@code baseName$n}, or {@code baseName$holder$n}
   * if {@param holder} is non-null.
   */
  private DexMethod internalCreateFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh,
      int index) {
    return createFreshMember(
        name -> {
          DexMethod tryMethod = createMethod(target, proto, name);
          if (isFresh.test(tryMethod)) {
            return Optional.of(tryMethod);
          } else {
            return Optional.empty();
          }
        },
        baseName,
        holder,
        index);
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form
   * baseName$holder$n, where {@code baseName} and {@code holder} are supplied by the user, and
   * {@code n} is picked to be the first number so that {@code isFresh.apply(method)} returns {@code
   * true}.
   *
   * @param holder indicates where the method originates from.
   */
  public DexMethodSignature createFreshMethodSignatureName(
      String baseName, DexType holder, DexProto proto, Predicate<DexMethodSignature> isFresh) {
    return createFreshMember(
        name -> {
          DexMethodSignature trySignature = DexMethodSignature.create(name, proto);
          if (isFresh.test(trySignature)) {
            return Optional.of(trySignature);
          } else {
            return Optional.empty();
          }
        },
        baseName,
        holder);
  }

  /**
   * Tries to find a method name for insertion into the class {@code holder} of the form baseName$n,
   * where {@code baseName} is supplied by the user, and {@code n} is picked to be the first number
   * so that {@code isFresh.apply(method)} returns {@code true}.
   */
  public DexField createFreshFieldNameWithoutHolder(
      DexType holder, DexType type, String baseName, Predicate<DexField> isFresh) {
    return internalCreateFreshFieldName(null, holder, type, baseName, isFresh);
  }

  private DexField internalCreateFreshFieldName(
      DexType originalHolder,
      DexType newHolder,
      DexType type,
      String baseName,
      Predicate<DexField> isFresh) {
    return createFreshMember(
        name -> {
          DexField candidate = createField(newHolder, type, name);
          return isFresh.test(candidate) ? Optional.of(candidate) : Optional.empty();
        },
        baseName,
        originalHolder);
  }

  public DexMethod createClassInitializer(DexType holder) {
    return createMethod(holder, createProto(voidType), classConstructorMethodName);
  }

  public DexMethod createInstanceInitializer(DexType holder, DexType... parameters) {
    return createMethod(holder, createProto(voidType, parameters), constructorMethodName);
  }

  public DexMethod createInstanceInitializer(DexType holder, DexTypeList parameters) {
    return createInstanceInitializer(holder, parameters.values);
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method, List<Supplier<DexType>> extraTypes, Predicate<DexMethod> isFresh) {
    return createInstanceInitializerWithFreshProto(method, extraTypes, isFresh, emptyConsumer());
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method,
      List<Supplier<DexType>> extraTypes,
      Predicate<DexMethod> isFresh,
      Consumer<Set<DexType>> usedExtraTypesConsumer) {
    assert method.isInstanceInitializer(this);
    return createInstanceInitializerWithFreshProto(
        method.proto,
        extraTypes,
        proto -> Optional.of(method.withProto(proto, this)).filter(isFresh),
        usedExtraTypesConsumer);
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method, DexType extraType, Predicate<DexMethod> isFresh) {
    assert method.isInstanceInitializer(this);
    return createInstanceInitializerWithFreshProto(
        method.proto,
        ImmutableList.of(() -> extraType),
        proto -> Optional.of(method.withProto(proto, this)).filter(isFresh),
        emptyConsumer());
  }

  private class FreshInstanceInitializerCandidate {

    DexProto protoWithoutExtraType;
    Supplier<DexType> extraTypeSupplier;
    Set<DexType> usedExtraTypes;

    FreshInstanceInitializerCandidate(
        DexProto protoWithoutExtraType,
        Supplier<DexType> extraTypeSupplier,
        Set<DexType> usedExtraTypes) {
      this.protoWithoutExtraType = protoWithoutExtraType;
      this.extraTypeSupplier = extraTypeSupplier;
      this.usedExtraTypes = SetUtils.newIdentityHashSet(usedExtraTypes);
    }

    DexProto createProto() {
      DexType extraType = extraTypeSupplier.get();
      usedExtraTypes.add(extraType);
      return appendTypeToProto(protoWithoutExtraType, extraType);
    }
  }

  private DexMethod createInstanceInitializerWithFreshProto(
      DexProto proto,
      List<Supplier<DexType>> extraTypes,
      Function<DexProto, Optional<DexMethod>> isFresh,
      Consumer<Set<DexType>> usedExtraTypesConsumer) {
    Optional<DexMethod> resultWithNoExtraTypes = isFresh.apply(proto);
    if (resultWithNoExtraTypes.isPresent()) {
      return resultWithNoExtraTypes.get();
    }
    assert !extraTypes.isEmpty();
    Deque<FreshInstanceInitializerCandidate> worklist =
        DequeUtils.newArrayDeque(
            new FreshInstanceInitializerCandidate(
                proto, extraTypes.iterator().next(), Collections.emptySet()));
    int count = 0;
    while (true) {
      assert count++ < 100;
      assert !worklist.isEmpty();
      FreshInstanceInitializerCandidate candidate = worklist.removeFirst();
      DexProto tryProto = candidate.createProto();
      Optional<DexMethod> object = isFresh.apply(tryProto);
      if (object.isPresent()) {
        assert !candidate.usedExtraTypes.isEmpty();
        usedExtraTypesConsumer.accept(candidate.usedExtraTypes);
        return object.get();
      }
      for (Supplier<DexType> extraTypeSupplier : extraTypes) {
        worklist.addLast(
            new FreshInstanceInitializerCandidate(
                tryProto, extraTypeSupplier, candidate.usedExtraTypes));
      }
    }
  }

  public DexString lookupString(int size, byte[] content) {
    return strings.get(new DexString(size, content));
  }

  public DexString lookupString(String source) {
    return strings.get(new DexString(source));
  }

  // Debugging support to extract marking string.
  // Find all markers.
  public synchronized Collection<Marker> extractMarkers() {
    Set<Marker> markers = new HashSet<>();
    for (DexString dexString : this.markers.keySet()) {
      Marker marker = Marker.parse(dexString);
      if (marker != null) {
        markers.add(marker);
      }
    }
    return markers;
  }

  // Non-synchronized internal create.
  private DexType internalCreateType(DexString descriptor) {
    assert !sorted;
    assert descriptor != null;
    DexType result = types.get(descriptor);
    if (result == null) {
      result = new DexType(descriptor);
      assert result.isArrayType()
              || result.isClassType()
              || result.isPrimitiveType()
              || result.isVoidType()
          : descriptor.toString();
      assert !isInternalSentinel(result);
      types.put(descriptor, result);
    }
    return result;
  }

  private DexType createStaticallyKnownType(String descriptor) {
    return createStaticallyKnownType(createString(descriptor));
  }

  private DexType createStaticallyKnownType(Class<?> clazz) {
    // This uses Class.getName() and not Class.getTypeName(), as the compilers are also
    // running on Art versions where Class.getTypeName() is not present (7.0 and before).
    return createStaticallyKnownType(
        createString(DescriptorUtils.javaTypeToDescriptor(clazz.getName())));
  }

  private DexType createStaticallyKnownType(DexString descriptor) {
    DexType type = internalCreateType(descriptor);
    // Conservatively add all statically known types to "compiler synthesized types set".
    addPossiblySynthesizedType(type);
    return type;
  }

  // Safe synchronized external create. May be used for statically known types in synthetic code.
  // See the generated BackportedMethods.java for reference.
  public synchronized DexType createSynthesizedType(String descriptor) {
    DexType type = internalCreateType(createString(descriptor));
    addPossiblySynthesizedType(type);
    return type;
  }

  // Registration of a type that is only dynamically known (eg, in the desugared lib spec), but
  // will be referenced during desugaring.
  public void registerTypeNeededForDesugaring(DexType type) {
    addPossiblySynthesizedType(type);
  }

  private void addPossiblySynthesizedType(DexType type) {
    if (type.isArrayType()) {
      type = type.toBaseType(this);
    }
    if (type.isClassType()) {
      possibleCompilerSynthesizedTypes.add(type);
    }
  }

  public boolean isPossiblyCompilerSynthesizedType(DexType type) {
    return possibleCompilerSynthesizedTypes.contains(type);
  }

  public void forEachPossiblyCompilerSynthesizedType(Consumer<DexType> fn) {
    possibleCompilerSynthesizedTypes.forEach(fn);
  }

  // Safe synchronized external create. Should never be used to create a statically known type!
  public synchronized DexType createType(DexString descriptor) {
    return internalCreateType(descriptor);
  }

  public DexType createType(String descriptor) {
    return createType(createString(descriptor));
  }

  public DexType createType(ClassReference clazz) {
    return createType(clazz.getDescriptor());
  }

  public DexType lookupType(DexString descriptor) {
    return types.get(descriptor);
  }

  public DexType createArrayType(int nesting, DexType baseType) {
    assert nesting > 0;
    return createType("[".repeat(nesting) + baseType.toDescriptorString());
  }

  public DexField createField(DexType clazz, DexType type, DexString name) {
    assert !sorted;
    DexField field = new DexField(clazz, type, name, skipNameValidationForTesting);
    return canonicalize(fields, field);
  }

  public DexField createField(DexType clazz, DexType type, String name) {
    return createField(clazz, type, createString(name));
  }

  public DexField createField(FieldReference fieldReference) {
    return createField(
        createType(fieldReference.getHolderClass().getDescriptor()),
        createType(fieldReference.getFieldType().getDescriptor()),
        fieldReference.getFieldName());
  }

  public DexProto createProto(DexType returnType, DexTypeList parameters) {
    assert !sorted;
    DexProto proto = new DexProto(returnType, parameters);
    return canonicalize(protos, proto);
  }

  public DexProto createProto(DexType returnType, DexType... parameters) {
    assert !sorted;
    return createProto(
        returnType, parameters.length == 0 ? DexTypeList.empty() : new DexTypeList(parameters));
  }

  public DexProto createProto(DexType returnType, List<DexType> parameters) {
    return createProto(returnType, parameters.toArray(DexType.EMPTY_ARRAY));
  }

  public DexProto prependHolderToProto(DexMethod method) {
    return method.getProto().prependParameter(method.getHolderType(), this);
  }

  public DexProto prependHolderToProtoIf(DexMethod method, boolean condition) {
    return condition ? prependHolderToProto(method) : method.getProto();
  }

  public DexProto appendTypeToProto(DexProto initialProto, DexType extraLastType) {
    DexType[] parameterTypes = new DexType[initialProto.parameters.size() + 1];
    System.arraycopy(
        initialProto.parameters.values, 0, parameterTypes, 0, initialProto.parameters.size());
    parameterTypes[parameterTypes.length - 1] = extraLastType;
    return createProto(initialProto.returnType, parameterTypes);
  }

  public DexMethod appendTypeToMethod(DexMethod initialMethod, DexType extraLastType) {
    DexProto newProto = appendTypeToProto(initialMethod.proto, extraLastType);
    return createMethod(initialMethod.holder, newProto, initialMethod.name);
  }

  @SuppressWarnings("ReferenceEquality")
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

  @SuppressWarnings("ReferenceEquality")
  private static DexType[] applyClassMappingToDexTypes(
      DexType[] types, Function<DexType, DexType> mapping) {
    Map<Integer, DexType> changed = new Int2ReferenceArrayMap<>();
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

  public DexMethod createMethod(DexType holder, DexProto proto, DexString name) {
    assert !sorted;
    DexMethod method = new DexMethod(holder, proto, name, skipNameValidationForTesting);
    return canonicalize(methods, method);
  }

  public DexMethod createMethod(DexType holder, DexProto proto, String name) {
    return createMethod(holder, proto, createString(name));
  }

  public DexMethod createMethod(MethodReference methodReference) {
    DexString[] formals = new DexString[methodReference.getFormalTypes().size()];
    ListUtils.forEachWithIndex(
        methodReference.getFormalTypes(),
        (formal, index) -> {
          formals[index] = createString(formal.getDescriptor());
        });
    return createMethod(
        createString(methodReference.getHolderClass().getDescriptor()),
        createString(methodReference.getMethodName()),
        methodReference.getReturnType() == null
            ? voidDescriptor
            : createString(methodReference.getReturnType().getDescriptor()),
        formals);
  }

  public DexMethodHandle createMethodHandle(
      MethodHandleType type,
      DexMember<? extends DexItem, ? extends DexMember<?, ?>> fieldOrMethod,
      boolean isInterface) {
    return createMethodHandle(type, fieldOrMethod, isInterface, null);
  }

  public DexMethodHandle createMethodHandle(
      MethodHandleType type,
      DexMember<? extends DexItem, ? extends DexMember<?, ?>> fieldOrMethod,
      boolean isInterface,
      DexMethod rewrittenTarget) {
    assert !sorted;
    DexMethodHandle methodHandle =
        new DexMethodHandle(type, fieldOrMethod, isInterface, rewrittenTarget);
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

  public DexMethod createMethod(
      DexString clazzDescriptor,
      DexString name,
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

  public DexMethod createClinitMethod(DexType holder) {
    return createMethod(holder, createProto(voidType), classConstructorMethodName);
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

  public Default createDefault(int lineDelta, int pcDelta) {
    return createDefault(Default.create(lineDelta, pcDelta).value);
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
  public SetPositionFrame createPositionFrame(Position position) {
    synchronized (setInlineFrames) {
      return setInlineFrames.computeIfAbsent(new SetPositionFrame(position), p -> p);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isConstructor(DexMethod method) {
    return method.name == constructorMethodName;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isClassConstructor(DexMethod method) {
    return method.name == classConstructorMethodName;
  }

  public void clearTypeElementsCache() {
    referenceTypes.clear();
    classTypeInterfaces.clear();
    leastUpperBoundOfInterfacesTable.clear();
  }

  public boolean verifyNoCachedTypeElements() {
    assert referenceTypes.isEmpty();
    assert classTypeInterfaces.isEmpty();
    assert leastUpperBoundOfInterfacesTable.isEmpty();
    return true;
  }

  public ReferenceTypeElement createReferenceTypeElement(
      DexType type, Nullability nullability, AppView<?> appView) {
    // Class case:
    // If two concurrent threads will try to create the same class-type the concurrent hash map will
    // synchronize on the type in .computeIfAbsent and only a single class type is created.
    //
    // Array case:
    // Arrays will create a lattice element for its base type thus we take special care here.
    // Multiple threads may race recursively to create a base type. We have two cases:
    // (i)  If base type is class type and the threads will race to create the class type but only a
    //      single one will be created (Class case).
    // (ii) If base is ArrayLattice case we can use our induction hypothesis to get that only one
    //      element is created for us up to this case. Threads will now race to return from the
    //      latest recursive call and fight to get access to .computeIfAbsent to add the
    //      ArrayTypeElement but only one will enter. The property that only one
    //      ArrayTypeElement is created per level therefore holds inductively.
    TypeElement memberType = null;
    if (type.isArrayType()) {
      ReferenceTypeElement existing = referenceTypes.get(type);
      if (existing != null) {
        return existing.getOrCreateVariant(nullability);
      }
      memberType =
          TypeElement.fromDexType(
              type.toArrayElementType(this), Nullability.maybeNull(), appView, true);
    }
    TypeElement finalMemberType = memberType;
    return referenceTypes
        .computeIfAbsent(
            type,
            t -> {
              if (type.isClassType()) {
                if (!appView.enableWholeProgramOptimizations()) {
                  // Don't reason at the level of interfaces in D8.
                  return ClassTypeElement.createForD8(type, nullability);
                }
                assert appView.appInfo().hasClassHierarchy();
                if (appView.isInterface(type).isTrue()) {
                  return ClassTypeElement.create(
                      objectType,
                      nullability,
                      appView.withClassHierarchy(),
                      InterfaceCollection.singleton(type));
                }
                // In theory, `interfaces` is the least upper bound of implemented interfaces.
                // It is expensive to walk through type hierarchy; collect implemented interfaces;
                // and compute the least upper bound of two interface sets. Hence, lazy
                // computations. Most likely during lattice join. See {@link
                // ClassTypeElement#getInterfaces}.
                return ClassTypeElement.create(type, nullability, appView.withClassHierarchy());
              }
              assert type.isArrayType();
              return ArrayTypeElement.create(finalMemberType, nullability);
            })
        .getOrCreateVariant(nullability);
  }

  public InterfaceCollection getOrComputeLeastUpperBoundOfImplementedInterfaces(
      DexType type, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return classTypeInterfaces.computeIfAbsent(
        type,
        t -> {
          InterfaceCollection itfs = appView.appInfo().implementedInterfaces(t);
          return computeLeastUpperBoundOfInterfaces(appView, itfs, itfs);
        });
  }

  @Deprecated
  synchronized public void forAllTypes(Consumer<DexType> f) {
    new ArrayList<>(types.values()).forEach(f);
  }
}
