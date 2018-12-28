// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.TestCondition.JAVA_RUNTIME;
import static com.android.tools.r8.TestCondition.R8_COMPILER;
import static com.android.tools.r8.TestCondition.and;
import static com.android.tools.r8.TestCondition.any;
import static com.android.tools.r8.TestCondition.anyDexVm;
import static com.android.tools.r8.TestCondition.artRuntimesFrom;
import static com.android.tools.r8.TestCondition.artRuntimesFromAndJava;
import static com.android.tools.r8.TestCondition.artRuntimesUpTo;
import static com.android.tools.r8.TestCondition.artRuntimesUpToAndJava;
import static com.android.tools.r8.TestCondition.cf;
import static com.android.tools.r8.TestCondition.match;
import static com.android.tools.r8.TestCondition.runtimes;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.R8RunArtTestsTest.DexTool;
import com.android.tools.r8.TestCondition.Runtime;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;

public class JctfTestSpecifications {

  public enum Outcome {
    PASSES,
    FAILS_WHEN_RUN,
    TIMEOUTS_WHEN_RUN,
    FLAKY_WHEN_RUN
  }

  public static final Multimap<String, TestCondition> failuresToTriage =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("math.BigInteger.nextProbablePrime.BigInteger_nextProbablePrime_A02", anyDexVm())
          .put("math.BigInteger.ConstructorLjava_lang_String.BigInteger_Constructor_A02", any())
          .put(
              "lang.StringBuffer.insertILjava_lang_Object.StringBuffer_insert_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT, Runtime.ART_V9_0_0, Runtime.ART_V8_1_0, Runtime.JAVA)))
          .put("lang.StringBuffer.serialization.StringBuffer_serialization_A01", anyDexVm())
          .put(
              "lang.CloneNotSupportedException.serialization.CloneNotSupportedException_serialization_A01",
              anyDexVm())
          .put(
              "lang.NumberFormatException.serialization.NumberFormatException_serialization_A01",
              anyDexVm())
          .put(
              "lang.StrictMath.roundF.StrictMath_round_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.JAVA)))
          .put(
              "lang.StrictMath.roundD.StrictMath_round_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.JAVA)))
          .put("lang.StrictMath.atan2DD.StrictMath_atan2_A01", any())
          .put("lang.Thread.stop.Thread_stop_A05", any())
          .put("lang.Thread.resume.Thread_resume_A02", anyDexVm())
          .put("lang.Thread.suspend.Thread_suspend_A02", anyDexVm())
          .put("lang.Thread.stop.Thread_stop_A03", any())
          .put("lang.Thread.interrupt.Thread_interrupt_A03", any())
          .put("lang.Thread.stop.Thread_stop_A04", any())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJ.Thread_Constructor_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1)))
          .put(
              "lang.Thread.getUncaughtExceptionHandler.Thread_getUncaughtExceptionHandler_A01",
              anyDexVm())
          .put("lang.Thread.getStackTrace.Thread_getStackTrace_A02", anyDexVm())
          .put("lang.Thread.enumerate_Ljava_lang_Thread.Thread_enumerate_A02", anyDexVm())
          .put("lang.Thread.countStackFrames.Thread_countStackFrames_A01", any())
          .put(
              "lang.Thread.getAllStackTraces.Thread_getAllStackTraces_A01",
              match(runtimes(Runtime.ART_V7_0_0)))
          .put("lang.Thread.destroy.Thread_destroy_A01", match(artRuntimesFrom(Runtime.ART_V4_4_4)))
          .put("lang.Thread.isAlive.Thread_isAlive_A01", anyDexVm())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A04", any())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A03", any())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A05", any())
          .put("lang.Thread.getPriority.Thread_getPriority_A01", anyDexVm())
          .put(
              "lang.Thread.getContextClassLoader.Thread_getContextClassLoader_A03",
              match(runtimes(Runtime.ART_V7_0_0)))
          .put("lang.OutOfMemoryError.serialization.OutOfMemoryError_serialization_A01", anyDexVm())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_StringLjava_lang_String.RuntimePermission_Constructor_A01",
              anyDexVm())
          .put(
              "lang.RuntimePermission.serialization.RuntimePermission_serialization_A01",
              anyDexVm())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_StringLjava_lang_String.RuntimePermission_Constructor_A02",
              anyDexVm())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_StringLjava_lang_String.RuntimePermission_Constructor_A03",
              anyDexVm())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A17", any())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_String.RuntimePermission_Constructor_A02",
              anyDexVm())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_String.RuntimePermission_Constructor_A03",
              anyDexVm())
          .put(
              "lang.RuntimePermission.ConstructorLjava_lang_String.RuntimePermission_Constructor_A01",
              anyDexVm())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A26", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A04", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A03", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A25", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A06", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A21", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A22", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A11", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A08", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A16", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A12", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A24", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A23", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A18", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A19", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A07", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A20", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A15", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A05", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A09", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A10", any())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A01", any())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A06",
              anyDexVm())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A14", any())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A05",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A04",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A03",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BII.ClassLoader_defineClass_A07",
              anyDexVm())
          .put(
              "lang.ClassLoader.setPackageAssertionStatusLjava_lang_StringZ.ClassLoader_setPackageAssertionStatus_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.setPackageAssertionStatusLjava_lang_StringZ.ClassLoader_setPackageAssertionStatus_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.setPackageAssertionStatusLjava_lang_StringZ.ClassLoader_setPackageAssertionStatus_A03",
              anyDexVm())
          .put("lang.ClassLoader.loadClassLjava_lang_StringZ.ClassLoader_loadClass_A03", anyDexVm())
          .put("lang.ClassLoader.loadClassLjava_lang_StringZ.ClassLoader_loadClass_A01", anyDexVm())
          .put("lang.ClassLoader.loadClassLjava_lang_StringZ.ClassLoader_loadClass_A04", anyDexVm())
          .put(
              "lang.ClassLoader.definePackageLjava_lang_String6Ljava_net_URL.ClassLoader_definePackage_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.definePackageLjava_lang_String6Ljava_net_URL.ClassLoader_definePackage_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.definePackageLjava_lang_String6Ljava_net_URL.ClassLoader_definePackage_A03",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A05",
              anyDexVm())
          .put(
              "lang.ClassLoader.getResourceLjava_lang_String.ClassLoader_getResource_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A06",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A03",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A07",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0)))
          .put(
              "lang.ClassLoader.defineClassLjava_lang_StringLjava_nio_ByteBufferLjava_security_ProtectionDomain.ClassLoader_defineClass_A04",
              anyDexVm())
          .put(
              "lang.ClassLoader.setSignersLjava_lang_Class_Ljava_lang_Object.ClassLoader_setSigners_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.clearAssertionStatus.ClassLoader_clearAssertionStatus_A01",
              anyDexVm())
          .put("lang.ClassLoader.Constructor.ClassLoader_Constructor_A02", anyDexVm())
          .put(
              "lang.ClassLoader.getSystemResourceLjava_lang_String.ClassLoader_getSystemResource_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.getResourcesLjava_lang_String.ClassLoader_getResources_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.resolveClassLjava_lang_Class.ClassLoader_resolveClass_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.getResourceAsStreamLjava_lang_String.ClassLoader_getResourceAsStream_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.findLoadedClassLjava_lang_String.ClassLoader_findLoadedClass_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.resolveClassLjava_lang_Class.ClassLoader_resolveClass_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.setDefaultAssertionStatusZ.ClassLoader_setDefaultAssertionStatus_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A05",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A06",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A08",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A03",
              anyDexVm())
          .put(
              "lang.ClassLoader.getSystemResourcesLjava_lang_String.ClassLoader_getSystemResources_A01",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A07",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A09",
              anyDexVm())
          .put(
              "lang.ClassLoader.defineClassLjava_lang_String_BIILjava_security_ProtectionDomain.ClassLoader_defineClass_A04",
              anyDexVm())
          .put("lang.ClassLoader.defineClass_BII.ClassLoader_defineClass_A02", anyDexVm())
          .put(
              "lang.ClassLoader.getSystemResourceAsStreamLjava_lang_String.ClassLoader_getSystemResourceAsStream_A01",
              anyDexVm())
          .put("lang.ClassLoader.getPackages.ClassLoader_getPackages_A01", anyDexVm())
          .put(
              "lang.ClassLoader.setClassAssertionStatusLjava_lang_StringZ.ClassLoader_setClassAssertionStatus_A01",
              any())
          .put("lang.ClassLoader.defineClass_BII.ClassLoader_defineClass_A03", anyDexVm())
          .put("lang.ClassLoader.defineClass_BII.ClassLoader_defineClass_A01", anyDexVm())
          .put("lang.ClassLoader.defineClass_BII.ClassLoader_defineClass_A04", anyDexVm())
          .put("lang.ClassLoader.getParent.ClassLoader_getParent_A01", anyDexVm())
          .put(
              "lang.ClassLoader.setClassAssertionStatusLjava_lang_StringZ.ClassLoader_setClassAssertionStatus_A04",
              any())
          .put(
              "lang.ClassLoader.setClassAssertionStatusLjava_lang_StringZ.ClassLoader_setClassAssertionStatus_A02",
              anyDexVm())
          .put("lang.ClassLoader.getParent.ClassLoader_getParent_A02", anyDexVm())
          .put(
              "lang.ClassLoader.getSystemClassLoader.ClassLoader_getSystemClassLoader_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.ConstructorLjava_lang_ClassLoader.ClassLoader_Constructor_A02",
              anyDexVm())
          .put(
              "lang.ClassLoader.findSystemClassLjava_lang_String.ClassLoader_findSystemClass_A04",
              anyDexVm())
          .put(
              "lang.ClassLoader.getPackageLjava_lang_String.ClassLoader_getPackage_A01", anyDexVm())
          .put(
              "lang.NoClassDefFoundError.serialization.NoClassDefFoundError_serialization_A01",
              anyDexVm())
          .put(
              "lang.TypeNotPresentException.serialization.TypeNotPresentException_serialization_A01",
              anyDexVm())
          .put(
              "lang.IndexOutOfBoundsException.serialization.IndexOutOfBoundsException_serialization_A01",
              anyDexVm())
          .put("lang.Enum.serialization.Enum_serialization_A01", anyDexVm())
          .put("lang.Enum.ConstructorLjava_lang_StringI.Enum_Constructor_A01", anyDexVm())
          .put("lang.InternalError.serialization.InternalError_serialization_A01", anyDexVm())
          .put("lang.Error.serialization.Error_serialization_A01", anyDexVm())
          .put("lang.Runtime.loadLjava_lang_String.Runtime_load_A02", any())
          .put("lang.Runtime.loadLjava_lang_String.Runtime_load_A05", any())
          .put("lang.Runtime.loadLjava_lang_String.Runtime_load_A03", any())
          .put("lang.Runtime.loadLjava_lang_String.Runtime_load_A04", any())
          .put("lang.Runtime.exec_Ljava_lang_String.Runtime_exec_A02", anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String.Runtime_exec_A03", anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String.Runtime_exec_A01", anyDexVm())
          .put("lang.Runtime.loadLibraryLjava_lang_String.Runtime_loadLibrary_A04", any())
          .put("lang.Runtime.loadLibraryLjava_lang_String.Runtime_loadLibrary_A05", any())
          .put("lang.Runtime.loadLibraryLjava_lang_String.Runtime_loadLibrary_A03", any())
          .put("lang.Runtime.execLjava_lang_String.Runtime_exec_A02", anyDexVm())
          .put("lang.Runtime.execLjava_lang_String.Runtime_exec_A03", anyDexVm())
          .put("lang.Runtime.loadLibraryLjava_lang_String.Runtime_loadLibrary_A02", any())
          .put("lang.Runtime.traceMethodCallsZ.Runtime_traceMethodCalls_A01", anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A01",
              anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A08",
              anyDexVm())
          .put("lang.Runtime.execLjava_lang_String.Runtime_exec_A01", anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A03",
              anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A07",
              anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A05",
              anyDexVm())
          .put(
              "lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A06",
              anyDexVm())
          .put("lang.Runtime.execLjava_lang_String_Ljava_lang_String.Runtime_exec_A03", anyDexVm())
          .put("lang.Runtime.execLjava_lang_String_Ljava_lang_String.Runtime_exec_A02", anyDexVm())
          .put("lang.Runtime.execLjava_lang_String_Ljava_lang_String.Runtime_exec_A01", anyDexVm())
          .put(
              "lang.Runtime.removeShutdownHookLjava_lang_Thread.Runtime_removeShutdownHook_A02",
              anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String_Ljava_lang_String.Runtime_exec_A01", anyDexVm())
          .put(
              "lang.Runtime.removeShutdownHookLjava_lang_Thread.Runtime_removeShutdownHook_A01",
              anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String_Ljava_lang_String.Runtime_exec_A02", anyDexVm())
          .put(
              "lang.Runtime.removeShutdownHookLjava_lang_Thread.Runtime_removeShutdownHook_A03",
              anyDexVm())
          .put(
              "lang.Runtime.exec_Ljava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A01",
              anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String_Ljava_lang_String.Runtime_exec_A03", anyDexVm())
          .put(
              "lang.Runtime.exec_Ljava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A02",
              anyDexVm())
          .put("lang.Runtime.haltI.Runtime_halt_A02", any())
          .put(
              "lang.Runtime.exec_Ljava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A03",
              anyDexVm())
          .put("lang.Runtime.haltI.Runtime_halt_A03", any())
          .put("lang.Runtime.runFinalizersOnExitZ.Runtime_runFinalizersOnExit_A01", anyDexVm())
          .put("lang.Runtime.haltI.Runtime_halt_A01", any())
          .put("lang.Runtime.runFinalizersOnExitZ.Runtime_runFinalizersOnExit_A03", anyDexVm())
          .put(
              "lang.Runtime.execLjava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A03",
              anyDexVm())
          .put(
              "lang.Runtime.execLjava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A01",
              anyDexVm())
          .put("lang.Runtime.runFinalizersOnExitZ.Runtime_runFinalizersOnExit_A02", anyDexVm())
          .put("lang.Runtime.exitI.Runtime_exit_A03", any())
          .put(
              "lang.Runtime.execLjava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A02",
              anyDexVm())
          .put("lang.Runtime.exitI.Runtime_exit_A04", any())
          .put(
              "lang.NoSuchMethodException.serialization.NoSuchMethodException_serialization_A01",
              anyDexVm())
          .put("lang.Runtime.exitI.Runtime_exit_A01", any())
          .put("lang.Runtime.exitI.Runtime_exit_A02", any())
          .put(
              "lang.InstantiationException.serialization.InstantiationException_serialization_A01",
              anyDexVm())
          .put("lang.Exception.serialization.Exception_serialization_A01", anyDexVm())
          .put(
              "lang.StackOverflowError.serialization.StackOverflowError_serialization_A01",
              anyDexVm())
          .put(
              "lang.NoSuchFieldException.serialization.NoSuchFieldException_serialization_A01",
              anyDexVm())
          .put(
              "lang.NegativeArraySizeException.serialization.NegativeArraySizeException_serialization_A01",
              anyDexVm())
          .put(
              "lang.ArrayIndexOutOfBoundsException.serialization.ArrayIndexOutOfBoundsException_serialization_A01",
              anyDexVm())
          .put("lang.VerifyError.serialization.VerifyError_serialization_A01", anyDexVm())
          .put(
              "lang.IllegalArgumentException.serialization.IllegalArgumentException_serialization_A01",
              anyDexVm())
          .put(
              "lang.IllegalStateException.serialization.IllegalStateException_serialization_A01",
              anyDexVm())
          .put("lang.Double.serialization.Double_serialization_A01", anyDexVm())
          .put("lang.Double.toStringD.Double_toString_A05", any())
          .put(
              "lang.ArithmeticException.serialization.ArithmeticException_serialization_A01",
              anyDexVm())
          .put(
              "lang.ExceptionInInitializerError.serialization.ExceptionInInitializerError_serialization_A01",
              anyDexVm())
          .put("lang.ThreadLocal.Class.ThreadLocal_class_A01", anyDexVm())
          .put("lang.Byte.serialization.Byte_serialization_A01", anyDexVm())
          .put(
              "lang.Byte.parseByteLjava_lang_StringI.Byte_parseByte_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Byte.valueOfLjava_lang_StringI.Byte_valueOf_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Byte.valueOfLjava_lang_String.Byte_ValueOf_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Byte.decodeLjava_lang_String.Byte_decode_A04",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put("lang.LinkageError.serialization.LinkageError_serialization_A01", anyDexVm())
          .put(
              "lang.ClassCastException.serialization.ClassCastException_serialization_A01",
              anyDexVm())
          .put(
              "lang.Byte.ConstructorLjava_lang_String.Byte_Constructor_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Byte.parseByteLjava_lang_String.Byte_parseByte_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put("lang.NoSuchFieldError.serialization.NoSuchFieldError_serialization_A01", anyDexVm())
          .put(
              "lang.UnsupportedOperationException.serialization.UnsupportedOperationException_serialization_A01",
              anyDexVm())
          .put(
              "lang.NoSuchMethodError.serialization.NoSuchMethodError_serialization_A01",
              anyDexVm())
          .put(
              "lang.IllegalMonitorStateException.serialization.IllegalMonitorStateException_serialization_A01",
              anyDexVm())
          .put(
              "lang.StringIndexOutOfBoundsException.serialization.StringIndexOutOfBoundsException_serialization_A01",
              anyDexVm())
          .put(
              "lang.SecurityException.serialization.SecurityException_serialization_A01",
              anyDexVm())
          .put(
              "lang.IllegalAccessError.serialization.IllegalAccessError_serialization_A01",
              anyDexVm())
          .put(
              "lang.ArrayStoreException.serialization.ArrayStoreException_serialization_A01",
              anyDexVm())
          .put("lang.UnknownError.serialization.UnknownError_serialization_A01", anyDexVm())
          .put("lang.Boolean.serialization.Boolean_serialization_A01", anyDexVm())
          .put(
              "lang.Integer.valueOfLjava_lang_StringI.Integer_valueOf_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put("lang.Integer.serialization.Integer_serialization_A01", anyDexVm())
          .put(
              "lang.Integer.parseIntLjava_lang_String.Integer_parseInt_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.getIntegerLjava_lang_StringI.Integer_getInteger_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.valueOfLjava_lang_String.Integer_valueOf_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.decodeLjava_lang_String.Integer_decode_A04",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.parseIntLjava_lang_StringI.Integer_parseInt_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.getIntegerLjava_lang_StringLjava_lang_Integer.Integer_getInteger_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.ConstructorLjava_lang_String.Integer_Constructor_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.Integer.getIntegerLjava_lang_String.Integer_getInteger_A02",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1,
                      Runtime.JAVA)))
          .put(
              "lang.ref.PhantomReference.isEnqueued.PhantomReference_isEnqueued_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1)))
          .put(
              "lang.ref.SoftReference.isEnqueued.SoftReference_isEnqueued_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1)))
          .put("lang.ref.SoftReference.get.SoftReference_get_A01", anyDexVm())
          .put("lang.ref.SoftReference.clear.SoftReference_clear_A01", cf())
          .put(
              "lang.ref.ReferenceQueue.poll.ReferenceQueue_poll_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V6_0_1,
                      Runtime.ART_V5_1_1)))
          .put(
              "lang.StackTraceElement.serialization.StackTraceElement_serialization_A01",
              anyDexVm())
          .put("lang.ref.WeakReference.get.WeakReference_get_A01", anyDexVm())
          .put(
              "lang.StackTraceElement.toString.StackTraceElement_toString_A01",
              match(runtimes(Runtime.ART_DEFAULT, Runtime.ART_V9_0_0, Runtime.ART_V8_1_0)))
          .put(
              "lang.NullPointerException.serialization.NullPointerException_serialization_A01",
              anyDexVm())
          .put(
              "lang.VirtualMachineError.serialization.VirtualMachineError_serialization_A01",
              anyDexVm())
          .put(
              "lang.ClassCircularityError.serialization.ClassCircularityError_serialization_A01",
              anyDexVm())
          .put("lang.ThreadDeath.serialization.ThreadDeath_serialization_A01", anyDexVm())
          .put(
              "lang.InstantiationError.serialization.InstantiationError_serialization_A01",
              anyDexVm())
          .put(
              "lang.IllegalThreadStateException.serialization.IllegalThreadStateException_serialization_A01",
              anyDexVm())
          .put("lang.ProcessBuilder.environment.ProcessBuilder_environment_A05", anyDexVm())
          .put("lang.ProcessBuilder.environment.ProcessBuilder_environment_A06", anyDexVm())
          .put("lang.ProcessBuilder.start.ProcessBuilder_start_A05", anyDexVm())
          .put("lang.ProcessBuilder.start.ProcessBuilder_start_A06", anyDexVm())
          .put("lang.ClassFormatError.serialization.ClassFormatError_serialization_A01", anyDexVm())
          .put("lang.Math.cbrtD.Math_cbrt_A01", match(artRuntimesFrom(Runtime.ART_V6_0_1)))
          .put("lang.Math.powDD.Math_pow_A08", anyDexVm())
          .put(
              "lang.IncompatibleClassChangeError.serialization.IncompatibleClassChangeError_serialization_A01",
              anyDexVm())
          .put("lang.Float.serialization.Float_serialization_A01", anyDexVm())
          .put("lang.Float.toStringF.Float_toString_A02", any())
          .put(
              "lang.Short.valueOfLjava_lang_StringI.Short_valueOf_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put(
              "lang.Short.valueOfLjava_lang_String.Short_valueOf_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put("lang.Short.serialization.Short_serialization_A01", anyDexVm())
          .put(
              "lang.Short.parseShortLjava_lang_String.Short_parseShort_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put(
              "lang.Short.decodeLjava_lang_String.Short_decode_A04",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put(
              "lang.Short.ConstructorLjava_lang_String.Short_Constructor_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put(
              "lang.ClassNotFoundException.serialization.ClassNotFoundException_serialization_A01",
              anyDexVm())
          .put(
              "lang.annotation.AnnotationFormatError.serialization.AnnotationFormatError_serialization_A01",
              anyDexVm())
          .put(
              "lang.Short.parseShortLjava_lang_StringI.Short_parseShort_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put(
              "lang.annotation.IncompleteAnnotationException.ConstructorLjava_lang_ClassLjava_lang_String.IncompleteAnnotationException_Constructor_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT, Runtime.ART_V9_0_0, Runtime.ART_V8_1_0, Runtime.JAVA)))
          .put(
              "lang.InterruptedException.serialization.InterruptedException_serialization_A01",
              anyDexVm())
          .put(
              "lang.annotation.IncompleteAnnotationException.Class.IncompleteAnnotationException_class_A01",
              any())
          .put("lang.annotation.Annotation.Class.Annotation_class_A03", anyDexVm())
          .put("lang.annotation.Annotation.serialization.Annotation_serialization_A01", anyDexVm())
          .put(
              "lang.annotation.Annotation.annotationType.Annotation_annotationType_A01", anyDexVm())
          .put(
              "lang.annotation.IncompleteAnnotationException.serialization.IncompleteAnnotationException_serialization_A01",
              anyDexVm())
          .put("lang.annotation.Annotation.Class.Annotation_class_A02", any())
          .put("lang.annotation.Retention.Retention_class_A01", anyDexVm())
          .put(
              "lang.annotation.AnnotationTypeMismatchException.Class.AnnotationTypeMismatchException_class_A01",
              any())
          .put("lang.Long.serialization.Long_serialization_A01", anyDexVm())
          .put("lang.ThreadGroup.resume.ThreadGroup_resume_A01", anyDexVm())
          .put("lang.ThreadGroup.resume.ThreadGroup_resume_A02", cf())
          .put("lang.ThreadGroup.suspend.ThreadGroup_suspend_A02", cf())
          .put(
              "lang.AbstractMethodError.serialization.AbstractMethodError_serialization_A01",
              anyDexVm())
          .put("lang.RuntimeException.serialization.RuntimeException_serialization_A01", anyDexVm())
          .put("lang.ThreadGroup.suspend.ThreadGroup_suspend_A01", anyDexVm())
          .put(
              "lang.ThreadGroup.ConstructorLjava_lang_ThreadGroupLjava_lang_String.ThreadGroup_Constructor_A03",
              anyDexVm())
          .put("lang.ThreadGroup.stop.ThreadGroup_stop_A01", anyDexVm())
          .put("lang.ThreadGroup.enumerate_Thread.ThreadGroup_enumerate_A01", anyDexVm())
          .put(
              "lang.ThreadGroup.ConstructorLjava_lang_ThreadGroupLjava_lang_String.ThreadGroup_Constructor_A04",
              anyDexVm())
          .put(
              "lang.ThreadGroup.parentOfLjava_lang_ThreadGroup.ThreadGroup_parentOf_A01",
              anyDexVm())
          .put("lang.ThreadGroup.getMaxPriority.ThreadGroup_getMaxPriority_A02", anyDexVm())
          .put("lang.ThreadGroup.checkAccess.ThreadGroup_checkAccess_A03", anyDexVm())
          .put("lang.ThreadGroup.enumerate_ThreadZ.ThreadGroup_enumerate_A01", anyDexVm())
          .put(
              "lang.ThreadGroup.uncaughtExceptionLjava_lang_ThreadLjava_lang_Throwable.ThreadGroup_uncaughtException_A01",
              anyDexVm())
          .put("lang.ThreadGroup.checkAccess.ThreadGroup_checkAccess_A02", anyDexVm())
          .put(
              "lang.ThreadGroup.ConstructorLjava_lang_String.ThreadGroup_Constructor_A04",
              anyDexVm())
          .put("lang.ThreadGroup.activeCount.ThreadGroup_activeCount_A01", anyDexVm())
          .put("lang.ThreadGroup.setMaxPriorityI.ThreadGroup_setMaxPriority_A03", anyDexVm())
          .put(
              "lang.ThreadGroup.ConstructorLjava_lang_String.ThreadGroup_Constructor_A03",
              anyDexVm())
          .put("lang.ThreadGroup.getParent.ThreadGroup_getParent_A03", anyDexVm())
          .put("lang.Class.getDeclaredConstructors.Class_getDeclaredConstructors_A02", any())
          .put("lang.AssertionError.serialization.AssertionError_serialization_A01", anyDexVm())
          .put("lang.Class.getClassLoader.Class_getClassLoader_A01", anyDexVm())
          .put("lang.Class.getDeclaringClass.Class_getDeclaringClass_A01", anyDexVm())
          .put(
              "lang.Class.getDeclaredFields.Class_getDeclaredFields_A01",
              match(artRuntimesFrom(Runtime.ART_V5_1_1)))
          .put("lang.Class.getClassLoader.Class_getClassLoader_A02", anyDexVm())
          .put("lang.Class.getClassLoader.Class_getClassLoader_A03", anyDexVm())
          .put("lang.Class.getDeclaredFields.Class_getDeclaredFields_A02", any())
          .put("lang.Class.getResourceLjava_lang_String.Class_getResource_A01", anyDexVm())
          .put("lang.Class.getConstructor_Ljava_lang_Class.Class_getConstructor_A03", anyDexVm())
          .put(
              "lang.Class.forNameLjava_lang_StringZLjava_lang_ClassLoader.Class_forName_A03", any())
          .put(
              "lang.Class.forNameLjava_lang_StringZLjava_lang_ClassLoader.Class_forName_A07",
              anyDexVm())
          .put(
              "lang.Class.forNameLjava_lang_StringZLjava_lang_ClassLoader.Class_forName_A01",
              anyDexVm())
          .put("lang.Class.getConstructor_Ljava_lang_Class.Class_getConstructor_A04", any())
          .put("lang.Class.serialization.Class_serialization_A01", anyDexVm())
          .put("lang.Class.getMethods.Class_getMethods_A02", any())
          .put(
              "lang.Class.getDeclaredMethodLjava_lang_String_Ljava_lang_Class.Class_getDeclaredMethod_A05",
              any())
          .put("lang.Class.getClasses.Class_getClasses_A02", any())
          .put(
              "lang.Class.getDeclaredMethodLjava_lang_String_Ljava_lang_Class.Class_getDeclaredMethod_A03",
              anyDexVm())
          .put(
              "lang.Class.getClasses.Class_getClasses_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V4_4_4,
                      Runtime.ART_V4_0_4,
                      Runtime.JAVA)))
          .put("lang.Class.getProtectionDomain.Class_getProtectionDomain_A01", any())
          .put("lang.Class.getProtectionDomain.Class_getProtectionDomain_A02", anyDexVm())
          .put(
              "lang.Class.getDeclaredMethods.Class_getDeclaredMethods_A01",
              match(artRuntimesFromAndJava(Runtime.ART_V7_0_0)))
          .put(
              "lang.Class.getMethods.Class_getMethods_A01",
              match(artRuntimesFromAndJava(Runtime.ART_V7_0_0)))
          .put("lang.Class.getGenericInterfaces.Class_getGenericInterfaces_A04", any())
          .put("lang.Class.getDeclaredFieldLjava_lang_String.Class_getDeclaredField_A04", any())
          .put("lang.Class.getDeclaredMethods.Class_getDeclaredMethods_A02", any())
          .put(
              "lang.Class.getResourceAsStreamLjava_lang_String.Class_getResourceAsStream_A01",
              anyDexVm())
          .put("lang.Class.getGenericInterfaces.Class_getGenericInterfaces_A05", any())
          .put("lang.Class.getAnnotationLjava_lang_Class.Class_getAnnotation_A01", any())
          .put("lang.Class.getGenericInterfaces.Class_getGenericInterfaces_A03", any())
          .put("lang.Class.getDeclaredClasses.Class_getDeclaredClasses_A02", any())
          .put("lang.Class.desiredAssertionStatus.Class_desiredAssertionStatus_A01", anyDexVm())
          .put("lang.Class.getPackage.Class_getPackage_A01", anyDexVm())
          .put("lang.Class.getFieldLjava_lang_String.Class_getField_A04", any())
          .put("lang.Class.getTypeParameters.Class_getTypeParameters_A02", any())
          .put("lang.Class.getDeclaredAnnotations.Class_getDeclaredAnnotations_A01", any())
          .put("lang.Class.getConstructors.Class_getConstructors_A02", any())
          .put(
              "lang.Class.isAnnotationPresentLjava_lang_Class.Class_isAnnotationPresent_A01", any())
          .put("lang.Class.getFields.Class_getFields_A02", any())
          .put("lang.Class.getGenericSuperclass.Class_getGenericSuperclass_A03", any())
          .put("lang.Class.getGenericSuperclass.Class_getGenericSuperclass_A04", any())
          .put("lang.Class.getSigners.Class_getSigners_A01", anyDexVm())
          .put(
              "lang.Class.getMethodLjava_lang_String_Ljava_lang_Class.Class_getMethod_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT,
                      Runtime.ART_V9_0_0,
                      Runtime.ART_V8_1_0,
                      Runtime.ART_V7_0_0,
                      Runtime.ART_V4_4_4,
                      Runtime.ART_V4_0_4)))
          .put(
              "lang.Class.getModifiers.Class_getModifiers_A03", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.Class.newInstance.Class_newInstance_A06", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.Class.getGenericSuperclass.Class_getGenericSuperclass_A01", any())
          .put("lang.Class.getGenericSuperclass.Class_getGenericSuperclass_A02", any())
          .put("lang.Class.newInstance.Class_newInstance_A07", any())
          .put(
              "lang.Class.getDeclaredConstructor_Ljava_lang_Class.Class_getDeclaredConstructor_A02",
              anyDexVm())
          .put("lang.Class.getMethodLjava_lang_String_Ljava_lang_Class.Class_getMethod_A05", any())
          .put("lang.Class.forNameLjava_lang_String.Class_forName_A01", anyDexVm())
          .put(
              "lang.Class.getDeclaredConstructor_Ljava_lang_Class.Class_getDeclaredConstructor_A03",
              any())
          .put(
              "lang.Class.getMethodLjava_lang_String_Ljava_lang_Class.Class_getMethod_A03",
              anyDexVm())
          .put("lang.Class.forNameLjava_lang_String.Class_forName_A02", any())
          .put(
              "lang.UnsatisfiedLinkError.serialization.UnsatisfiedLinkError_serialization_A01",
              anyDexVm())
          .put("lang.Class.getAnnotations.Class_getAnnotations_A01", any())
          .put(
              "lang.EnumConstantNotPresentException.serialization.EnumConstantNotPresentException_serialization_A01",
              anyDexVm())
          .put("lang.String.toLowerCase.String_toLowerCase_A01", any())
          .put("lang.String.splitLjava_lang_StringI.String_split_A01", any())
          .put("lang.String.serialization.String_serialization_A01", anyDexVm())
          .put("lang.String.regionMatchesZILjava_lang_StringII.String_regionMatches_A01", any())
          .put("lang.String.valueOfF.String_valueOf_A01", any())
          .put("lang.String.Constructor_BLjava_nio_charset_Charset.String_Constructor_A01", any())
          .put("lang.String.concatLjava_lang_String.String_concat_A01", anyDexVm())
          .put("lang.String.matchesLjava_lang_String.String_matches_A01", anyDexVm())
          .put(
              "lang.String.CASE_INSENSITIVE_ORDER.serialization.String_serialization_A01",
              anyDexVm())
          .put(
              "lang.String.getBytesLjava_lang_String.String_getBytes_A14",
              match(artRuntimesUpTo(Runtime.ART_V7_0_0)))
          .put("lang.String.splitLjava_lang_String.String_split_A01", any())
          .put("lang.String.getBytesII_BI.String_getBytes_A03", any())
          .put("lang.String.getBytesII_BI.String_getBytes_A02", anyDexVm())
          .put("lang.String.toLowerCaseLjava_util_Locale.String_toLowerCase_A01", any())
          .put("lang.String.Constructor_BIILjava_nio_charset_Charset.String_Constructor_A01", any())
          .put("lang.String.getBytesLjava_nio_charset_Charset.String_getBytes_A01", anyDexVm())
          .put("lang.String.valueOfD.String_valueOf_A01", any())
          .put(
              "lang.String.getBytesLjava_nio_charset_Charset.String_getBytes_A14",
              match(artRuntimesUpTo(Runtime.ART_V7_0_0)))
          .put("lang.Package.isSealed.Package_isSealed_A01", anyDexVm())
          .put(
              "lang.Package.getSpecificationVersion.Package_getSpecificationVersion_A01",
              anyDexVm())
          .put("lang.Package.getAnnotationLjava_lang_Class.Package_getAnnotation_A01", any())
          .put(
              "lang.Package.isAnnotationPresentLjava_lang_Class.Package_isAnnotationPresent_A02",
              match(and(runtimes(Runtime.ART_V4_0_4), artRuntimesFrom(Runtime.ART_V7_0_0))))
          .put("lang.Package.getName.Package_getName_A01", anyDexVm())
          .put(
              "lang.Package.getImplementationVersion.Package_getImplementationVersion_A01",
              anyDexVm())
          .put("lang.Package.getDeclaredAnnotations.Package_getDeclaredAnnotations_A01", any())
          .put("lang.Package.getSpecificationVendor.Package_getSpecificationVendor_A01", anyDexVm())
          .put(
              "lang.Package.getAnnotationLjava_lang_Class.Package_getAnnotation_A02",
              match(and(artRuntimesFrom(Runtime.ART_V7_0_0), runtimes(Runtime.ART_V4_0_4))))
          .put(
              "lang.Package.isCompatibleWithLjava_lang_String.Package_isCompatibleWith_A01",
              anyDexVm())
          .put("lang.Package.toString.Package_toString_A01", anyDexVm())
          .put("lang.Package.getAnnotations.Package_getAnnotations_A01", any())
          .put(
              "lang.Package.isAnnotationPresentLjava_lang_Class.Package_isAnnotationPresent_A01",
              any())
          .put("lang.Package.getSpecificationTitle.Package_getSpecificationTitle_A01", anyDexVm())
          .put("lang.Package.getImplementationTitle.Package_getImplementationTitle_A01", anyDexVm())
          .put("lang.Package.getPackages.Package_getPackages_A01", anyDexVm())
          .put("lang.Package.hashCode.Package_hashCode_A01", anyDexVm())
          .put("lang.Package.getPackageLjava_lang_String.Package_getPackage_A01", anyDexVm())
          .put(
              "lang.Package.getImplementationVendor.Package_getImplementationVendor_A01",
              anyDexVm())
          .put("lang.Package.isSealedLjava_net_URL.Package_isSealed_A01", anyDexVm())
          .put("lang.StringBuilder.serialization.StringBuilder_serialization_A01", anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_io_FileDescriptor.SecurityManager_checkRead_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAwtEventQueueAccess.SecurityManager_checkAwtEventQueueAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkWriteLjava_lang_String.SecurityManager_checkWrite_A02",
              anyDexVm())
          .put("lang.SecurityManager.inClassLoader.SecurityManager_inClassLoader_A01", anyDexVm())
          .put(
              "lang.SecurityManager.checkPermissionLjava_security_PermissionLjava_lang_Object.SecurityManager_checkPermission_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_io_FileDescriptor.SecurityManager_checkRead_A01",
              anyDexVm())
          .put("lang.SecurityManager.inCheck.SecurityManager_inCheck_A01", anyDexVm())
          .put(
              "lang.SecurityManager.currentClassLoader.SecurityManager_currentClassLoader_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPrintJobAccess.SecurityManager_checkPrintJobAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkWriteLjava_lang_String.SecurityManager_checkWrite_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPackageAccessLjava_lang_String.SecurityManager_checkPackageAccess_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAcceptLjava_lang_StringI.SecurityManager_checkAccept_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPermissionLjava_security_PermissionLjava_lang_Object.SecurityManager_checkPermission_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.currentClassLoader.SecurityManager_currentClassLoader_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkMulticastLjava_net_InetAddress.SecurityManager_checkMulticast_A02",
              anyDexVm())
          .put("lang.SecurityManager.checkListenI.SecurityManager_checkListen_A01", anyDexVm())
          .put(
              "lang.SecurityManager.getSecurityContext.SecurityManager_getSecurityContext_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPackageAccessLjava_lang_String.SecurityManager_checkPackageAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkMemberAccessLjava_lang_ClassI.SecurityManager_checkMemberAccess_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkMulticastLjava_net_InetAddressB.SecurityManager_checkMulticast_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAcceptLjava_lang_StringI.SecurityManager_checkAccept_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkMulticastLjava_net_InetAddress.SecurityManager_checkMulticast_A01",
              anyDexVm())
          .put("lang.SecurityManager.Constructor.SecurityManager_Constructor_A01", anyDexVm())
          .put("lang.SecurityManager.getClassContext.SecurityManager_getClassContext_A01", any())
          .put(
              "lang.SecurityManager.checkMemberAccessLjava_lang_ClassI.SecurityManager_checkMemberAccess_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkDeleteLjava_lang_String.SecurityManager_checkDelete_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_lang_StringLjava_lang_Object.SecurityManager_checkRead_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkMulticastLjava_net_InetAddressB.SecurityManager_checkMulticast_A01",
              anyDexVm())
          .put("lang.SecurityManager.checkListenI.SecurityManager_checkListen_A02", any())
          .put(
              "lang.SecurityManager.checkAccessLjava_lang_Thread.SecurityManager_checkAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkWriteLjava_io_FileDescriptor.SecurityManager_checkWrite_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkDeleteLjava_lang_String.SecurityManager_checkDelete_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPropertiesAccess.SecurityManager_checkPropertiesAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_lang_StringLjava_lang_Object.SecurityManager_checkRead_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAccessLjava_lang_ThreadGroup.SecurityManager_checkAccess_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAccessLjava_lang_Thread.SecurityManager_checkAccess_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPackageDefinitionLjava_lang_String.SecurityManager_checkPackageDefinition_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_lang_String.SecurityManager_checkRead_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkWriteLjava_io_FileDescriptor.SecurityManager_checkWrite_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_lang_StringLjava_lang_Object.SecurityManager_checkRead_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkExecLjava_lang_String.SecurityManager_checkExec_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPackageDefinitionLjava_lang_String.SecurityManager_checkPackageDefinition_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkExecLjava_lang_String.SecurityManager_checkExec_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkCreateClassLoader.SecurityManager_checkCreateClassLoader_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkReadLjava_lang_String.SecurityManager_checkRead_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkAccessLjava_lang_ThreadGroup.SecurityManager_checkAccess_A01",
              anyDexVm())
          .put("lang.ThreadGroup.interrupt.ThreadGroup_interrupt_A02", cf())
          .put(
              "lang.SecurityManager.inClassLjava_lang_String.SecurityManager_inClass_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringILjava_lang_Object.SecurityManager_checkConnect_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkExecLjava_lang_String.SecurityManager_checkExec_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkSetFactory.SecurityManager_checkSetFactory_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringILjava_lang_Object.SecurityManager_checkConnect_A04",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPermissionLjava_security_Permission.SecurityManager_checkPermission_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.inClassLjava_lang_String.SecurityManager_inClass_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.inClassLjava_lang_String.SecurityManager_inClass_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPropertyAccessLjava_lang_String.SecurityManager_checkPropertyAccess_A02",
              anyDexVm())
          .put("lang.SecurityManager.checkExitI.SecurityManager_checkExit_A01", anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringILjava_lang_Object.SecurityManager_checkConnect_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringILjava_lang_Object.SecurityManager_checkConnect_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.classLoaderDepth.SecurityManager_classLoaderDepth_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.classDepthLjava_lang_String.SecurityManager_classDepth_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPropertyAccessLjava_lang_String.SecurityManager_checkPropertyAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkPropertyAccessLjava_lang_String.SecurityManager_checkPropertyAccess_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringI.SecurityManager_checkConnect_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkLinkLjava_lang_String.SecurityManager_checkLink_A02",
              anyDexVm())
          .put("lang.SecurityManager.classLoaderDepth.SecurityManager_classLoaderDepth_A01", any())
          .put(
              "lang.SecurityManager.checkPermissionLjava_security_Permission.SecurityManager_checkPermission_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.currentLoadedClass.SecurityManager_currentLoadedClass_A01",
              any())
          .put(
              "lang.SecurityManager.checkSecurityAccessLjava_lang_String.SecurityManager_checkSecurityAccess_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringI.SecurityManager_checkConnect_A03",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkConnectLjava_lang_StringI.SecurityManager_checkConnect_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkTopLevelWindowLjava_lang_Object.SecurityManager_checkTopLevelWindow_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.currentLoadedClass.SecurityManager_currentLoadedClass_A02",
              anyDexVm())
          .put(
              "lang.SecurityManager.classDepthLjava_lang_String.SecurityManager_classDepth_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkSecurityAccessLjava_lang_String.SecurityManager_checkSecurityAccess_A01",
              anyDexVm())
          .put("lang.Throwable.serialization.Throwable_serialization_A01", anyDexVm())
          .put(
              "lang.SecurityManager.checkTopLevelWindowLjava_lang_Object.SecurityManager_checkTopLevelWindow_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkLinkLjava_lang_String.SecurityManager_checkLink_A01",
              anyDexVm())
          .put("lang.Throwable.getStackTrace.Throwable_getStackTrace_A01", any())
          .put(
              "lang.SecurityManager.checkSystemClipboardAccess.SecurityManager_checkSystemClipboardAccess_A01",
              anyDexVm())
          .put(
              "lang.SecurityManager.checkSecurityAccessLjava_lang_String.SecurityManager_checkSecurityAccess_A02",
              anyDexVm())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_String.ReflectPermission_Constructor_A03",
              anyDexVm())
          .put(
              "lang.reflect.MalformedParameterizedTypeException.serialization.MalformedParameterizedTypeException_serialization_A01",
              anyDexVm())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_StringLjava_lang_String.ReflectPermission_Constructor_A02",
              anyDexVm())
          .put(
              "lang.UnsupportedClassVersionError.serialization.UnsupportedClassVersionError_serialization_A01",
              anyDexVm())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_String.ReflectPermission_Constructor_A01",
              any())
          .put("lang.reflect.ReflectPermission.Class.ReflectPermission_class_A01", anyDexVm())
          .put("lang.reflect.Proxy.serialization.Proxy_serialization_A01", any())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_StringLjava_lang_String.ReflectPermission_Constructor_A03",
              anyDexVm())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_String.ReflectPermission_Constructor_A02",
              anyDexVm())
          .put("lang.reflect.ReflectPermission.Class.ReflectPermission_class_A02", anyDexVm())
          .put(
              "lang.reflect.ReflectPermission.Constructor_java_lang_StringLjava_lang_String.ReflectPermission_Constructor_A01",
              any())
          .put(
              "lang.reflect.Proxy.getInvocationHandlerLjava_lang_Object.Proxy_getInvocationHandler_A02",
              match(artRuntimesFromAndJava(Runtime.ART_V5_1_1)))
          .put("lang.reflect.Proxy.Class.Proxy_class_A01", any())
          .put(
              "lang.reflect.Proxy.Class.Proxy_class_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.reflect.Proxy.Class.Proxy_class_A03",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.reflect.Proxy.getProxyClassLjava_lang_ClassLoader_Ljava_lang_Class.Proxy_getProxyClass_A01",
              anyDexVm())
          .put(
              "lang.reflect.Proxy.getProxyClassLjava_lang_ClassLoader_Ljava_lang_Class.Proxy_getProxyClass_A03",
              anyDexVm())
          .put(
              "lang.reflect.Proxy.h.Proxy_h_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT, Runtime.ART_V9_0_0, Runtime.ART_V8_1_0, Runtime.JAVA)))
          .put("lang.reflect.Proxy.serialization.Proxy_serialization_A02", any())
          .put(
              "lang.reflect.GenericSignatureFormatError.serialization.GenericSignatureFormatError_serialization_A01",
              anyDexVm())
          .put(
              "lang.reflect.Proxy.newProxyInstanceLjava_lang_ClassLoader_Ljava_lang_ClassLjava_lang_reflect_InvocationHandler.Proxy_newProxyInstance_A02",
              anyDexVm())
          .put(
              "lang.reflect.Proxy.ConstructorLjava_lang_reflect_InvocationHandler.Proxy_Constructor_A01",
              match(
                  runtimes(
                      Runtime.ART_DEFAULT, Runtime.ART_V9_0_0, Runtime.ART_V8_1_0, Runtime.JAVA)))
          .put(
              "lang.reflect.Proxy.newProxyInstanceLjava_lang_ClassLoader_Ljava_lang_ClassLjava_lang_reflect_InvocationHandler.Proxy_newProxyInstance_A01",
              anyDexVm())
          .put("lang.reflect.Modifier.isStrictI.Modifier_isStrict_A01", any())
          .put("lang.reflect.Method.getGenericReturnType.Method_getGenericReturnType_A03", any())
          .put("lang.reflect.Method.getGenericReturnType.Method_getGenericReturnType_A02", any())
          .put("lang.reflect.Method.getAnnotationLjava_lang_Class.Method_getAnnotation_A01", any())
          .put(
              "lang.reflect.Method.getGenericExceptionTypes.Method_getGenericExceptionTypes_A02",
              any())
          .put("lang.reflect.Method.isBridge.Method_isBridge_A01", any())
          .put("lang.reflect.Method.isSynthetic.Method_isSynthetic_A01", any())
          .put("lang.reflect.Method.getGenericReturnType.Method_getGenericReturnType_A04", any())
          .put(
              "lang.reflect.Method.getGenericExceptionTypes.Method_getGenericExceptionTypes_A01",
              any())
          .put(
              "lang.reflect.Method.invokeLjava_lang_Object_Ljava_lang_Object.Method_invoke_A07",
              anyDexVm())
          .put(
              "lang.reflect.Method.getGenericExceptionTypes.Method_getGenericExceptionTypes_A04",
              any())
          .put("lang.reflect.Method.getTypeParameters.Method_getTypeParameters_A02", any())
          .put(
              "lang.reflect.Method.getGenericExceptionTypes.Method_getGenericExceptionTypes_A03",
              any())
          .put(
              "lang.reflect.Method.getDeclaredAnnotations.Method_getDeclaredAnnotations_A01", any())
          .put(
              "lang.reflect.Method.getGenericParameterTypes.Method_getGenericParameterTypes_A04",
              any())
          .put("lang.reflect.Method.toGenericString.Method_toGenericString_A01", any())
          .put(
              "lang.reflect.Method.getGenericParameterTypes.Method_getGenericParameterTypes_A03",
              any())
          .put(
              "lang.reflect.InvocationHandler.invokeLjava_lang_ObjectLjava_lang_reflect_Method_Ljava_lang_Object.InvocationHandler_invoke_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.reflect.InvocationHandler.invokeLjava_lang_ObjectLjava_lang_reflect_Method_Ljava_lang_Object.InvocationHandler_invoke_A02",
              anyDexVm())
          .put("lang.reflect.Method.getDefaultValue.Method_getDefaultValue_A02", any())
          .put(
              "lang.reflect.Method.hashCode.Method_hashCode_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Method.toString.Method_toString_A01", any())
          .put(
              "lang.reflect.Method.getGenericParameterTypes.Method_getGenericParameterTypes_A02",
              any())
          .put("lang.reflect.Field.getFloatLjava_lang_Object.Field_getFloat_A05", anyDexVm())
          .put("lang.reflect.Field.getDeclaringClass.Field_getDeclaringClass_A01", anyDexVm())
          .put("lang.reflect.Field.getByteLjava_lang_Object.Field_getByte_A05", anyDexVm())
          .put("lang.reflect.Field.getCharLjava_lang_Object.Field_getChar_A05", anyDexVm())
          .put("lang.reflect.Field.getBooleanLjava_lang_Object.Field_getBoolean_A05", anyDexVm())
          .put("lang.reflect.Field.setByteLjava_lang_ObjectB.Field_setByte_A05", anyDexVm())
          .put("lang.reflect.Field.setByteLjava_lang_ObjectB.Field_setByte_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setByteLjava_lang_ObjectB.Field_setByte_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setBooleanLjava_lang_ObjectZ.Field_setBoolean_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setBooleanLjava_lang_ObjectZ.Field_setBoolean_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setCharLjava_lang_ObjectC.Field_setChar_A05", anyDexVm())
          .put("lang.reflect.Field.isSynthetic.Field_isSynthetic_A01", any())
          .put("lang.reflect.Field.setBooleanLjava_lang_ObjectZ.Field_setBoolean_A05", anyDexVm())
          .put("lang.reflect.Field.getType.Field_getType_A01", anyDexVm())
          .put("lang.reflect.Field.setCharLjava_lang_ObjectC.Field_setChar_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setCharLjava_lang_ObjectC.Field_setChar_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.getDoubleLjava_lang_Object.Field_getDouble_A05", anyDexVm())
          .put("lang.reflect.Field.setFloatLjava_lang_ObjectF.Field_setFloat_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setFloatLjava_lang_ObjectF.Field_setFloat_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.getAnnotationLjava_lang_Class.Field_getAnnotation_A01", any())
          .put(
              "lang.reflect.Field.setIntLjava_lang_ObjectI.Field_setInt_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.getIntLjava_lang_Object.Field_getInt_A05", anyDexVm())
          .put("lang.reflect.Field.setFloatLjava_lang_ObjectF.Field_setFloat_A05", anyDexVm())
          .put("lang.reflect.Field.getShortLjava_lang_Object.Field_getShort_A05", anyDexVm())
          .put("lang.reflect.Field.getGenericType.Field_getGenericType_A03", any())
          .put("lang.reflect.Field.getDeclaredAnnotations.Field_getDeclaredAnnotations_A01", any())
          .put("lang.reflect.Field.getGenericType.Field_getGenericType_A01", anyDexVm())
          .put("lang.reflect.Field.setIntLjava_lang_ObjectI.Field_setInt_A05", anyDexVm())
          .put("lang.reflect.Field.getGenericType.Field_getGenericType_A02", any())
          .put("lang.reflect.Field.toGenericString.Field_toGenericString_A01", anyDexVm())
          .put("lang.reflect.Field.getGenericType.Field_getGenericType_A04", any())
          .put("lang.reflect.Field.setIntLjava_lang_ObjectI.Field_setInt_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setDoubleLjava_lang_ObjectD.Field_setDouble_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setDoubleLjava_lang_ObjectD.Field_setDouble_A05", anyDexVm())
          .put("lang.reflect.Field.setShortLjava_lang_ObjectS.Field_setShort_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setLongLjava_lang_ObjectJ.Field_setLong_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setLongLjava_lang_ObjectJ.Field_setLong_A05", anyDexVm())
          .put("lang.reflect.Field.setLongLjava_lang_ObjectJ.Field_setLong_A01", anyDexVm())
          .put("lang.reflect.Field.setDoubleLjava_lang_ObjectD.Field_setDouble_A01", anyDexVm())
          .put(
              "lang.reflect.Field.setShortLjava_lang_ObjectS.Field_setShort_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setShortLjava_lang_ObjectS.Field_setShort_A05", anyDexVm())
          .put("lang.reflect.Field.getLjava_lang_Object.Field_get_A05", anyDexVm())
          .put("lang.reflect.Field.getLongLjava_lang_Object.Field_getLong_A05", anyDexVm())
          .put(
              "lang.reflect.Field.setLjava_lang_ObjectLjava_lang_Object.Field_set_A02",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.reflect.Field.setLjava_lang_ObjectLjava_lang_Object.Field_set_A05", anyDexVm())
          .put("lang.reflect.Field.setLjava_lang_ObjectLjava_lang_Object.Field_set_A01", anyDexVm())
          .put(
              "lang.reflect.Constructor.newInstance_Ljava_lang_Object.Constructor_newInstance_A06",
              anyDexVm())
          .put("lang.reflect.Constructor.isSynthetic.Constructor_isSynthetic_A01", any())
          .put(
              "lang.reflect.Constructor.getGenericExceptionTypes.Constructor_getGenericExceptionTypes_A03",
              any())
          .put(
              "lang.reflect.Constructor.getGenericExceptionTypes.Constructor_getGenericExceptionTypes_A02",
              any())
          .put(
              "lang.reflect.Constructor.getGenericExceptionTypes.Constructor_getGenericExceptionTypes_A01",
              any())
          .put(
              "lang.reflect.Constructor.getAnnotationLjava_lang_Class.Constructor_getAnnotation_A01",
              any())
          .put(
              "lang.reflect.Constructor.getDeclaredAnnotations.Constructor_getDeclaredAnnotations_A01",
              any())
          .put(
              "lang.reflect.Constructor.getGenericExceptionTypes.Constructor_getGenericExceptionTypes_A04",
              any())
          .put(
              "lang.reflect.InvocationTargetException.serialization.InvocationTargetException_serialization_A01",
              anyDexVm())
          .put(
              "lang.reflect.Constructor.toGenericString.Constructor_toGenericString_A01",
              anyDexVm())
          .put(
              "lang.reflect.Constructor.getTypeParameters.Constructor_getTypeParameters_A02", any())
          .put(
              "lang.reflect.Constructor.getGenericParameterTypes.Constructor_getGenericParameterTypes_A03",
              any())
          .put(
              "lang.reflect.Constructor.getGenericParameterTypes.Constructor_getGenericParameterTypes_A04",
              any())
          .put(
              "lang.reflect.Constructor.getGenericParameterTypes.Constructor_getGenericParameterTypes_A02",
              any())
          .put(
              "lang.reflect.AccessibleObject.setAccessibleZ.AccessibleObject_setAccessible_A03",
              anyDexVm())
          .put(
              "lang.reflect.UndeclaredThrowableException.serialization.UndeclaredThrowableException_serialization_A01",
              anyDexVm())
          .put(
              "lang.reflect.AccessibleObject.setAccessibleZ.AccessibleObject_setAccessible_A02",
              anyDexVm())
          .put(
              "lang.reflect.AccessibleObject.setAccessible_Ljava_lang_reflect_AccessibleObjectZ.AccessibleObject_setAccessible_A03",
              anyDexVm())
          .put(
              "lang.reflect.AccessibleObject.isAnnotationPresentLjava_lang_Class.AccessibleObject_isAnnotationPresent_A01",
              any())
          .put(
              "lang.reflect.AccessibleObject.setAccessible_Ljava_lang_reflect_AccessibleObjectZ.AccessibleObject_setAccessible_A02",
              anyDexVm())
          .put(
              "lang.reflect.AccessibleObject.getAnnotations.AccessibleObject_getAnnotations_A01",
              any())
          .put(
              "lang.reflect.AccessibleObject.getDeclaredAnnotations.AccessibleObject_getDeclaredAnnotations_A01",
              any())
          .put(
              "lang.reflect.AccessibleObject.getAnnotationLjava_lang_Class.AccessibleObject_getAnnotation_A01",
              any())
          .put(
              "lang.IllegalAccessException.serialization.IllegalAccessException_serialization_A01",
              anyDexVm())
          .put("lang.Character.getTypeI.Character_getType_A01", any())
          .put("lang.Character.isDigitI.Character_isDigit_A01", any())
          .put("lang.Character.getTypeC.Character_getType_A01", any())
          .put("lang.Character.serialization.Character_serialization_A01", anyDexVm())
          .put("lang.Character.isDigitC.Character_isDigit_A01", any())
          .put("lang.Character.digitCI.Character_digit_A01", any())
          .put("lang.Character.digitII.Character_digit_A01", any())
          .put("lang.Character.isLowerCaseC.Character_isLowerCase_A01", anyDexVm())
          .put(
              "lang.Character.isSpaceCharC.Character_isSpaceChar_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.Character.isSpaceCharI.Character_isSpaceChar_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.Character.isWhitespaceC.Character_isWhitespace_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.Character.isWhitespaceI.Character_isWhitespace_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.Character.getDirectionalityI.Character_getDirectionality_A01", any())
          .put(
              "lang.Character.UnicodeBlock.ofC.UnicodeBlock_of_A01",
              match(artRuntimesFromAndJava(Runtime.ART_V4_4_4)))
          .put(
              "lang.Character.UnicodeBlock.ofI.UnicodeBlock_of_A01",
              match(artRuntimesFromAndJava(Runtime.ART_V4_4_4)))
          .put("lang.Character.isLowerCaseI.Character_isLowerCase_A01", anyDexVm())
          .put("lang.Process.waitFor.Process_waitFor_A01", anyDexVm())
          .put("lang.System.getProperties.System_getProperties_A01", anyDexVm())
          .put("lang.Process.getErrorStream.Process_getErrorStream_A01", anyDexVm())
          .put("lang.Character.getDirectionalityC.Character_getDirectionality_A01", any())
          .put("lang.Process.exitValue.Process_exitValue_A01", anyDexVm())
          .put("lang.System.loadLjava_lang_String.System_load_A01", anyDexVm())
          .put("lang.Process.getInputStream.Process_getInputStream_A01", anyDexVm())
          .put("lang.System.loadLibraryLjava_lang_String.System_loadLibrary_A01", anyDexVm())
          .put(
              "lang.System.setSecurityManagerLjava_lang_SecurityManager.System_setSecurityManager_A02",
              anyDexVm())
          .put("lang.System.runFinalizersOnExitZ.System_runFinalizersOnExit_A01", anyDexVm())
          .put("lang.System.getenvLjava_lang_String.System_getenv_A01", anyDexVm())
          .put("lang.System.getenv.System_getenv_A01", anyDexVm())
          .put(
              "lang.System.getPropertyLjava_lang_StringLjava_lang_String.System_getProperty_A01",
              anyDexVm())
          .put("lang.System.exitI.System_exit_A01", anyDexVm())
          .put(
              "util.concurrent.ArrayBlockingQueue.serialization.ArrayBlockingQueue_serialization_A01",
              anyDexVm())
          .put(
              "lang.System.arraycopyLjava_lang_ObjectILjava_lang_ObjectII.System_arraycopy_A04",
              anyDexVm())
          .put(
              "lang.System.setPropertiesLjava_util_Properties.System_setProperties_A02", anyDexVm())
          .put("lang.System.clearPropertyLjava_lang_String.System_clearProperty_A02", anyDexVm())
          .put("lang.System.getPropertyLjava_lang_String.System_getProperty_A01", anyDexVm())
          .put(
              "util.concurrent.LinkedBlockingQueue.serialization.LinkedBlockingQueue_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.LinkedBlockingDeque.serialization.LinkedBlockingDeque_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.ConcurrentLinkedQueue.serialization.ConcurrentLinkedQueue_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.SynchronousQueue.serialization.SynchronousQueue_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.CopyOnWriteArrayList.serialization.CopyOnWriteArrayList_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.CopyOnWriteArrayList.subListII.CopyOnWriteArrayList_subList_A01",
              any())
          .put(
              "util.concurrent.ConcurrentHashMap.serialization.ConcurrentHashMap_serialization_A01",
              match(artRuntimesFrom(Runtime.ART_V5_1_1)))
          .put("util.concurrent.ConcurrentHashMap.keySet.ConcurrentHashMap_keySet_A01", anyDexVm())
          .put(
              "util.concurrent.Executors.privilegedThreadFactory.Executors_privilegedThreadFactory_A01",
              any())
          .put(
              "util.concurrent.Executors.privilegedCallableLjava_util_concurrent_Callable.Executors_privilegedCallable_A01",
              anyDexVm())
          .put(
              "util.concurrent.CopyOnWriteArraySet.serialization.CopyOnWriteArraySet_serialization_A01",
              anyDexVm())
          .put(
              "util.concurrent.Executors.privilegedCallableUsingCurrentClassLoaderLjava_util_concurrent_Callable.Executors_privilegedCallableUsingCurrentClassLoader_A01",
              anyDexVm())
          .put(
              "util.concurrent.PriorityBlockingQueue.ConstructorLjava_util_Collection.PriorityBlockingQueue_Constructor_A01",
              any())
          .put(
              "util.concurrent.PriorityBlockingQueue.serialization.PriorityBlockingQueue_serialization_A01",
              anyDexVm())
          .put(
              "lang.ThreadGroup.destroy.ThreadGroup_destroy_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put("lang.Thread.start.Thread_start_A01", match(runtimes(Runtime.ART_V7_0_0)))
          .put(
              "lang.String.getBytesLjava_lang_String.String_getBytes_A02",
              match(artRuntimesUpTo(Runtime.ART_V7_0_0)))
          .put(
              "util.concurrent.CopyOnWriteArrayList.lastIndexOfLjava_lang_ObjectI.CopyOnWriteArrayList_lastIndexOf_A02",
              match(artRuntimesUpTo(Runtime.ART_V7_0_0)))
          .put(
              "util.concurrent.CopyOnWriteArrayList.lastIndexOfLjava_lang_ObjectI.CopyOnWriteArrayList_lastIndexOf_A01",
              match(artRuntimesUpTo(Runtime.ART_V7_0_0)))
          .put(
              "lang.StringBuffer.getCharsII_CI.StringBuffer_getChars_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StringBuffer.appendF.StringBuffer_append_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StringBuffer.insertI_CII.StringBuffer_insert_A02",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StrictMath.scalbDI.StrictMath_scalb_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StrictMath.scalbDI.StrictMath_scalb_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StrictMath.scalbFI.StrictMath_scalb_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StrictMath.scalbFI.StrictMath_scalb_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJ.Thread_Constructor_A07",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_String.Thread_Constructor_A07",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Thread.toString.Thread_toString_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put("lang.Thread.start.Thread_start_A02", match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Thread.setPriorityI.Thread_setPriority_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ClassLoader.ConstructorLjava_lang_ClassLoader.ClassLoader_Constructor_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Enum.compareToLjava_lang_Enum.Enum_compareTo_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put("lang.Enum.hashCode.Enum_hashCode_A01", match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StackTraceElement.hashCode.StackTraceElement_hashCode_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ProcessBuilder.environment.ProcessBuilder_environment_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ProcessBuilder.environment.ProcessBuilder_environment_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Float.toStringF.Float_toString_A04", match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Float.toStringF.Float_toString_A03", match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.getMaxPriority.ThreadGroup_getMaxPriority_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.uncaughtExceptionLjava_lang_ThreadLjava_lang_Throwable.ThreadGroup_uncaughtException_A02",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.list.ThreadGroup_list_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.setMaxPriorityI.ThreadGroup_setMaxPriority_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.setMaxPriorityI.ThreadGroup_setMaxPriority_A04",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.ThreadGroup.toString.ThreadGroup_toString_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Class.getFieldLjava_lang_String.Class_getField_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.String.replaceCC.String_replace_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.Package.isCompatibleWithLjava_lang_String.Package_isCompatibleWith_A02",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StringBuilder.appendF.StringBuilder_append_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.StringBuilder.insertIF.StringBuilder_insert_A01",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.reflect.AccessibleObject.setAccessibleZ.AccessibleObject_setAccessible_A04",
              match(artRuntimesUpToAndJava(Runtime.ART_V4_4_4)))
          .put(
              "lang.reflect.AccessibleObject.setAccessible_Ljava_lang_reflect_AccessibleObjectZ.AccessibleObject_setAccessible_A04",
              match(artRuntimesUpToAndJava(Runtime.ART_V6_0_1)))
          .put(
              "lang.Character.UnicodeBlock.forName_java_lang_String.UnicodeBlock_forName_A03",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put(
              "lang.System.loadLjava_lang_String.System_load_A02",
              match(artRuntimesUpTo(Runtime.ART_V6_0_1)))
          .put("lang.Math.hypotDD.Math_hypot_A04", match(artRuntimesUpTo(Runtime.ART_V5_1_1)))
          .put(
              "math.BigInteger.probablePrimeIjava_util_Random.BigInteger_probablePrime_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.Math.sqrtD.Math_sqrt_A01", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.StrictMath.cbrtD.StrictMath_cbrt_A01", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.StrictMath.log10D.StrictMath_log10_A01", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.StrictMath.powDD.StrictMath_pow_A01", match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.String.indexOfII.String_indexOf_A01", match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.String.indexOfLjava_lang_StringI.String_indexOf_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getByteLjava_lang_ObjectI.Array_getByte_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getDoubleLjava_lang_ObjectI.Array_getDouble_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getDoubleLjava_lang_ObjectI.Array_getDouble_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getFloatLjava_lang_ObjectI.Array_getFloat_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getFloatLjava_lang_ObjectI.Array_getFloat_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getIntLjava_lang_ObjectI.Array_getInt_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getIntLjava_lang_ObjectI.Array_getInt_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getLongLjava_lang_ObjectI.Array_getLong_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getLongLjava_lang_ObjectI.Array_getLong_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.getShortLjava_lang_ObjectI.Array_getShort_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.setBooleanLjava_lang_ObjectIZ.Array_setBoolean_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.setCharLjava_lang_ObjectIC.Array_setChar_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.setLjava_lang_ObjectILjava_lang_Object.Array_set_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Array.setLjava_lang_ObjectILjava_lang_Object.Array_set_A03",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "lang.reflect.Constructor.toString.Constructor_toString_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "math.BigInteger.modPowLjava_math_BigIntegerLjava_math_Integer.BigInteger_modPow_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "util.concurrent.LinkedBlockingDeque.drainToLjava_util_CollectionI.LinkedBlockingDeque_drainTo_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put(
              "util.concurrent.LinkedBlockingQueue.drainToLjava_util_CollectionI.LinkedBlockingQueue_drainTo_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A02", cf())
          .put(
              "lang.AssertionError.ConstructorLjava_lang_Object.AssertionError_Constructor_A01",
              match(runtimes(Runtime.ART_V4_0_4)))
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A13", cf())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A01", cf())
          .put("lang.Runtime.addShutdownHookLjava_lang_Thread.Runtime_addShutdownHook_A02", cf())
          .put("lang.ThreadGroup.destroy.ThreadGroup_destroy_A04", cf())
          .put("lang.ThreadGroup.setMaxPriorityI.ThreadGroup_setMaxPriority_A02", cf())
          .put(
              "lang.String.replaceFirstLjava_lang_StringLjava_lang_String.String_replaceFirst_A01",
              cf())
          .put(
              "lang.String.replaceAllLjava_lang_StringLjava_lang_String.String_replaceAll_A01",
              cf())
          .put("lang.System.inheritedChannel.System_inheritedChannel_A01", cf())
          .build(); // end of failuresToTriage

  public static final Multimap<String, TestCondition> flakyWhenRun =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("lang.Object.notifyAll.Object_notifyAll_A03", anyDexVm())
          .put("lang.Object.notify.Object_notify_A03", anyDexVm())
          .put(
              "util.concurrent.ConcurrentSkipListSet.addLjava_lang_Object.ConcurrentSkipListSet_add_A01",
              any())
          .put("util.concurrent.SynchronousQueue.ConstructorZ", anyDexVm())
          .put("lang.Thread.interrupt.Thread_interrupt_A04", anyDexVm())
          .put(
              "util.concurrent.SynchronousQueue.ConstructorZ.SynchronousQueue_Constructor_A01",
              anyDexVm())
          .put("lang.Thread.getState.Thread_getState_A01", anyDexVm())
          .put(
              "util.concurrent.ScheduledThreadPoolExecutor.getTaskCount.ScheduledThreadPoolExecutor_getTaskCount_A01",
              any())
          .put(
              "lang.ref.PhantomReference.clear.PhantomReference_clear_A01",
              match(artRuntimesUpToAndJava(Runtime.ART_V4_4_4)))
          .put(
              "lang.ref.SoftReference.clear.SoftReference_clear_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.ref.WeakReference.clear.WeakReference_clear_A01",
              match(and(artRuntimesUpTo(Runtime.ART_V4_4_4), JAVA_RUNTIME)))
          .put(
              "lang.ref.PhantomReference.isEnqueued.PhantomReference_isEnqueued_A01",
              match(
                  and(
                      runtimes(Runtime.ART_V9_0_0, Runtime.ART_V8_1_0),
                      artRuntimesUpTo(Runtime.ART_V4_4_4))))
          .put("lang.ref.WeakReference.isEnqueued.WeakReference_isEnqueued_A01", anyDexVm())
          .put(
              "lang.ref.WeakReference.enqueue.WeakReference_enqueue_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.ref.SoftReference.isEnqueued.SoftReference_isEnqueued_A01",
              match(
                  and(
                      runtimes(Runtime.ART_V9_0_0, Runtime.ART_V8_1_0),
                      artRuntimesUpTo(Runtime.ART_V4_4_4))))
          .put(
              "lang.ref.SoftReference.enqueue.SoftReference_enqueue_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put(
              "lang.ref.ReferenceQueue.poll.ReferenceQueue_poll_A01",
              match(artRuntimesUpTo(Runtime.ART_V4_4_4)))
          .put("lang.Runtime.gc.Runtime_gc_A01", cf())
          .put(
              "util.concurrent.AbstractExecutorService.invokeAllLjava_util_CollectionJLjava_util_concurrent_TimeUnit.AbstractExecutorService_invokeAll_A06",
              match(runtimes(Runtime.ART_V4_0_4)))
          .build(); // end of flakyWhenRun

  public static final Multimap<String, TestCondition> timeoutsWhenRun =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("lang.Thread.interrupt.Thread_interrupt_A01", anyDexVm())
          .put("lang.Thread.resume.Thread_resume_A01", anyDexVm())
          .put("lang.Thread.stop.Thread_stop_A01", anyDexVm())
          .put("lang.Thread.suspend.Thread_suspend_A01", anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJ.Thread_Constructor_A04",
              anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJ.Thread_Constructor_A03",
              anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJ.Thread_Constructor_A05",
              anyDexVm())
          .put("lang.Thread.setNameLjava_lang_String.Thread_setName_A02", anyDexVm())
          .put("lang.Thread.stop.Thread_stop_A02", anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_String.Thread_Constructor_A02",
              anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_String.Thread_Constructor_A03",
              anyDexVm())
          .put("lang.Thread.getStackTrace.Thread_getStackTrace_A03", anyDexVm())
          .put(
              "lang.Thread.setDefaultUncaughtExceptionHandler.Thread_setDefaultUncaughtExceptionHandler_A02",
              anyDexVm())
          .put("lang.Thread.checkAccess.Thread_checkAccess_A01", anyDexVm())
          .put(
              "lang.Thread.ConstructorLjava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_String.Thread_Constructor_A04",
              anyDexVm())
          .put(
              "lang.Thread.setUncaughtExceptionHandler.Thread_setUncaughtExceptionHandler_A02",
              anyDexVm())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A01", anyDexVm())
          .put("lang.Thread.getAllStackTraces.Thread_getAllStackTraces_A02", anyDexVm())
          .put(
              "lang.Thread.setContextClassLoaderLjava_lang_ClassLoader.Thread_setContextClassLoader_A02",
              anyDexVm())
          .put("lang.Thread.setPriorityI.Thread_setPriority_A02", anyDexVm())
          .put("lang.Thread.stopLjava_lang_Throwable.Thread_stop_A02", anyDexVm())
          .put(
              "lang.Runtime.execLjava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A04",
              anyDexVm())
          .put("lang.Thread.getContextClassLoader.Thread_getContextClassLoader_A02", anyDexVm())
          .put("lang.ThreadGroup.suspend.ThreadGroup_suspend_A02", anyDexVm())
          .put("lang.Thread.setDaemonZ.Thread_setDaemon_A03", anyDexVm())
          .put("lang.ProcessBuilder.environment.ProcessBuilder_environment_A07", anyDexVm())
          .put(
              "lang.Runtime.exec_Ljava_lang_String_Ljava_lang_StringLjava_io_File.Runtime_exec_A04",
              anyDexVm())
          .put("lang.Runtime.execLjava_lang_String_Ljava_lang_String.Runtime_exec_A04", anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String.Runtime_exec_A04", anyDexVm())
          .put("lang.Runtime.execLjava_lang_String.Runtime_exec_A04", anyDexVm())
          .put("lang.System.clearPropertyLjava_lang_String.System_clearProperty_A03", anyDexVm())
          .put("lang.System.getSecurityManager.System_getSecurityManager_A01", anyDexVm())
          .put("lang.System.setInLjava_io_InputStream.System_setIn_A02", anyDexVm())
          .put("lang.System.setOutLjava_io_PrintStream.System_setOut_A02", anyDexVm())
          .put("lang.ThreadGroup.destroy.ThreadGroup_destroy_A04", anyDexVm())
          .put("lang.ThreadGroup.enumerate_ThreadGroupZ.ThreadGroup_enumerate_A03", anyDexVm())
          .put("lang.ThreadGroup.enumerate_Thread.ThreadGroup_enumerate_A03", anyDexVm())
          .put("lang.ThreadGroup.enumerate_ThreadZ.ThreadGroup_enumerate_A03", anyDexVm())
          .put("lang.ThreadGroup.interrupt.ThreadGroup_interrupt_A02", anyDexVm())
          .put("lang.ThreadGroup.resume.ThreadGroup_resume_A02", anyDexVm())
          .put("lang.ThreadGroup.setMaxPriorityI.ThreadGroup_setMaxPriority_A02", anyDexVm())
          .put("lang.Runtime.exec_Ljava_lang_String_Ljava_lang_String.Runtime_exec_A04", anyDexVm())
          .put("lang.System.getenvLjava_lang_String.System_getenv_A03", anyDexVm())
          .put(
              "lang.System.setPropertyLjava_lang_StringLjava_lang_String.System_setProperty_A02",
              anyDexVm())
          .put("lang.ThreadGroup.enumerate_ThreadGroup.ThreadGroup_enumerate_A03", anyDexVm())
          .put("lang.ThreadGroup.getParent.ThreadGroup_getParent_A02", anyDexVm())
          .put("lang.ThreadGroup.setDaemonZ.ThreadGroup_setDaemon_A02", anyDexVm())
          .put("lang.ThreadGroup.stop.ThreadGroup_stop_A02", any())
          .put("lang.Class.getSuperclass.Class_getSuperclass_A01", anyDexVm())
          .put("lang.System.getenv.System_getenv_A03", anyDexVm())
          .put("lang.System.inheritedChannel.System_inheritedChannel_A01", anyDexVm())
          .put(
              "util.concurrent.ArrayBlockingQueue.containsLjava_lang_Object.ArrayBlockingQueue_contains_A01",
              anyDexVm())
          .put(
              "lang.System.arraycopyLjava_lang_ObjectILjava_lang_ObjectII.System_arraycopy_A03",
              anyDexVm())
          .put("lang.System.setErrLjava_io_PrintStream.System_setErr_A02", anyDexVm())
          .put(
              "util.concurrent.ArrayBlockingQueue.containsLjava_lang_Object.ArrayBlockingQueue_contains_A01",
              anyDexVm())
          .put(
              "lang.System.setSecurityManagerLjava_lang_SecurityManager.System_setSecurityManager_A01",
              anyDexVm())
          .put(
              "util.concurrent.ArrayBlockingQueue.containsLjava_lang_Object.ArrayBlockingQueue_contains_A01",
              anyDexVm())
          .put(
              "util.concurrent.ArrayBlockingQueue.containsLjava_lang_Object.ArrayBlockingQueue_contains_A01",
              anyDexVm())
          .put(
              "lang.System.setPropertiesLjava_util_Properties.System_setProperties_A01", anyDexVm())
          .put(
              "util.concurrent.CopyOnWriteArrayList.ConstructorLjava_util_Collection.CopyOnWriteArrayList_Constructor_A02",
              anyDexVm())
          .put("util.concurrent.CyclicBarrier.reset.CyclicBarrier_reset_A03", any())
          .put("lang.System.clearPropertyLjava_lang_String.System_clearProperty_A01", anyDexVm())
          .put("lang.System.getenv.System_getenv_A04", anyDexVm())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A02", anyDexVm())
          .put("lang.RuntimePermission.Class.RuntimePermission_class_A13", anyDexVm())
          .build(); // end of timeoutsWhenRun

  public static final Multimap<String, TestCondition> requiresInliningDisabled =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("lang.Throwable.printStackTrace.Throwable_printStackTrace_A01", match(R8_COMPILER))
          .put("lang.Throwable.printStackTraceLjava_io_PrintWriter.Throwable_printStackTrace_A01",
              match(R8_COMPILER))
          .put("lang.Throwable.printStackTraceLjava_io_PrintStream.Throwable_printStackTrace_A01",
              match(R8_COMPILER))
          .put("lang.ref.SoftReference.isEnqueued.SoftReference_isEnqueued_A01", match(R8_COMPILER))
          .put("lang.ref.WeakReference.isEnqueued.WeakReference_isEnqueued_A01", match(R8_COMPILER))
          .put("lang.StackTraceElement.getMethodName.StackTraceElement_getMethodName_A01",
              match(R8_COMPILER))
          .put("lang.Thread.dumpStack.Thread_dumpStack_A01", match(R8_COMPILER))
          .build();

  public static final Set<String> compilationFailsWithAsmMethodTooLarge = ImmutableSet.of();

  private static final boolean testMatch(
      Multimap<String, TestCondition> testConditions,
      String name,
      CompilerUnderTest compilerUnderTest,
      Runtime runtime,
      CompilationMode compilationMode) {
    Collection<TestCondition> entries = testConditions.get(name);
    for (TestCondition entry : entries) {
      if (entry.test(DexTool.NONE, compilerUnderTest, runtime, compilationMode)) {
        return true;
      }
    }
    return false;
  }

  public static final <T> T getExpectedOutcome(
      String name,
      CompilerUnderTest compilerUnderTest,
      Runtime runtime,
      CompilationMode compilationMode,
      BiFunction<Outcome, Boolean, T> consumer) {

    Outcome outcome = null;

    if (testMatch(failuresToTriage, name, compilerUnderTest, runtime, compilationMode)) {
      outcome = Outcome.FAILS_WHEN_RUN;
    }
    if (testMatch(timeoutsWhenRun, name, compilerUnderTest, runtime, compilationMode)) {
      assert outcome == null;
      outcome = Outcome.TIMEOUTS_WHEN_RUN;
    }
    if (testMatch(flakyWhenRun, name, compilerUnderTest, runtime, compilationMode)) {
      assert outcome == null;
      outcome = Outcome.FLAKY_WHEN_RUN;
    }
    if (outcome == null) {
      outcome = Outcome.PASSES;
    }
    boolean disableInlining =
        testMatch(requiresInliningDisabled, name, compilerUnderTest, runtime, compilationMode);
    return consumer.apply(outcome, disableInlining);
  }
}
