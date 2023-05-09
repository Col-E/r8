// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class AndroidApiLevelDatabaseHelper {

  public static Set<String> notModeledTypes() {
    // The below types are known not to be modeled by any api-versions.
    Set<String> notModeledTypes = new HashSet<>();
    notModeledTypes.add("androidx.annotation.RecentlyNullable");
    notModeledTypes.add("androidx.annotation.RecentlyNonNull");
    notModeledTypes.add("android.annotation.Nullable");
    notModeledTypes.add("android.annotation.NonNull");
    return notModeledTypes;
  }

  static void visitAdditionalKnownApiReferences(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    addStringBuilderAndBufferMethods(factory, apiLevelConsumer);
    addConcurrentKeySetViewMethods(factory, apiLevelConsumer);
    addNfcMethods(factory, apiLevelConsumer);
    addWebkitCookieSyncManagerMethods(factory, apiLevelConsumer);
    addChronoTimeMethods(factory, apiLevelConsumer);
  }

  private static void addStringBuilderAndBufferMethods(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    // StringBuilder and StringBuffer lack api definitions for the exact same methods in
    // api-versions.xml. See b/216587554 for related error.
    for (DexType type : new DexType[] {factory.stringBuilderType, factory.stringBufferType}) {
      apiLevelConsumer.accept(
          factory.createMethod(type, factory.createProto(factory.intType), "capacity"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.intType), "codePointAt"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.intType), "codePointBefore"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.intType, factory.intType),
              "codePointCount"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.voidType, factory.intType), "ensureCapacity"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(
                  factory.voidType,
                  factory.intType,
                  factory.intType,
                  factory.charArrayType,
                  factory.intType),
              "getChars"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.stringType), "indexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.stringType, factory.intType),
              "indexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.stringType), "lastIndexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.stringType, factory.intType),
              "lastIndexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.intType, factory.intType),
              "offsetByCodePoints"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.voidType, factory.intType, factory.charType),
              "setCharAt"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.voidType, factory.intType), "setLength"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.stringType, factory.intType), "substring"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.stringType, factory.intType, factory.intType),
              "substring"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(type, factory.createProto(factory.voidType), "trimToSize"),
          AndroidApiLevel.B);
    }
  }

  private static void addConcurrentKeySetViewMethods(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    // KeysetView.getMap was also added in N (24).
    apiLevelConsumer.accept(
        factory.createMethod(
            factory.concurrentHashMapKeySetViewType,
            factory.createProto(factory.concurrentHashMapType),
            "getMap"),
        AndroidApiLevel.N);
  }

  private static void addNfcMethods(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    String[] nfcClasses =
        new String[] {
          "Landroid/nfc/tech/Ndef;",
          "Landroid/nfc/tech/NfcA;",
          "Landroid/nfc/tech/NfcB;",
          "Landroid/nfc/tech/NfcBarcode;",
          "Landroid/nfc/tech/NfcF;",
          "Landroid/nfc/tech/NdefFormatable;",
          "Landroid/nfc/tech/IsoDep;",
          "Landroid/nfc/tech/MifareClassic;",
          "Landroid/nfc/tech/MifareUltralight;",
          "Landroid/nfc/tech/NfcV;"
        };
    DexType tagType = factory.createType("Landroid/nfc/Tag;");
    // Seems like all methods are available from api level G_MR1 but we choose K since some of these
    // classes are introduced at 17.
    for (String nfcClass : nfcClasses) {
      DexType nfcClassType = factory.createType(nfcClass);
      apiLevelConsumer.accept(
          factory.createMethod(
              nfcClassType, factory.createProto(factory.booleanType), "isConnected"),
          AndroidApiLevel.K);
      apiLevelConsumer.accept(
          factory.createMethod(nfcClassType, factory.createProto(tagType), "getTag"),
          AndroidApiLevel.K);
      apiLevelConsumer.accept(
          factory.createMethod(nfcClassType, factory.createProto(factory.voidType), "close"),
          AndroidApiLevel.K);
      apiLevelConsumer.accept(
          factory.createMethod(nfcClassType, factory.createProto(factory.voidType), "connect"),
          AndroidApiLevel.K);
    }
  }

  private static void addWebkitCookieSyncManagerMethods(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    // All of these are added in android.jar from at least 14.
    DexType cookieSyncManager = factory.createType("Landroid/webkit/CookieSyncManager;");
    DexProto voidProto = factory.createProto(factory.voidType);
    for (String methodName : new String[] {"sync", "resetSync", "startSync", "stopSync", "run"}) {
      apiLevelConsumer.accept(
          factory.createMethod(cookieSyncManager, voidProto, methodName), AndroidApiLevel.I);
    }
  }

  private static void addChronoTimeMethods(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    DexType valueRangeType = factory.createType("Ljava/time/temporal/ValueRange;");
    DexType chronoLocalDateType = factory.createType("Ljava/time/chrono/ChronoLocalDate;");
    DexType temporalType = factory.createType("Ljava/time/temporal/Temporal;");
    DexType temporalFieldType = factory.createType("Ljava/time/temporal/TemporalField;");
    DexType temporalUnitType = factory.createType("Ljava/time/temporal/TemporalUnit;");
    DexType temporalAmountType = factory.createType("Ljava/time/temporal/TemporalAmount;");
    DexType temporalAdjusterType = factory.createType("Ljava/time/temporal/TemporalAdjuster;");

    // All of these classes was added in 26.
    String[] timeClasses =
        new String[] {
          "Ljava/time/chrono/JapaneseDate;",
          "Ljava/time/chrono/MinguoDate;",
          "Ljava/time/chrono/HijrahDate;",
          "Ljava/time/chrono/ThaiBuddhistDate;"
        };
    for (String timeClass : timeClasses) {
      DexType timeType = factory.createType(timeClass);
      // int lengthOfMonth()
      apiLevelConsumer.accept(
          factory.createMethod(timeType, factory.createProto(factory.intType), "lengthOfMonth"),
          AndroidApiLevel.O);
      // int lengthOfYear()
      apiLevelConsumer.accept(
          factory.createMethod(timeType, factory.createProto(factory.intType), "lengthOfYear"),
          AndroidApiLevel.O);
      // boolean isSupported(java.time.temporal.TemporalField)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType, factory.createProto(factory.booleanType, temporalFieldType), "isSupported"),
          AndroidApiLevel.O);
      // java.time.temporal.ValueRange range(java.time.temporal.TemporalField)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType, factory.createProto(valueRangeType, temporalFieldType), "range"),
          AndroidApiLevel.O);
      // long getLong(java.time.temporal.TemporalField)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType, factory.createProto(factory.longType, temporalFieldType), "getLong"),
          AndroidApiLevel.O);
      // java.time.chrono.ChronoLocalDateTime atTime(java.time.LocalTime)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType,
              factory.createProto(
                  factory.createType("Ljava/time/chrono/ChronoLocalDateTime;"),
                  factory.createType("Ljava/time/LocalTime;")),
              "atTime"),
          AndroidApiLevel.O);
      // java.time.chrono.ChronoPeriod
      // java.time.chrono.JapaneseDate.until(java.time.chrono.ChronoLocalDate)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType,
              factory.createProto(
                  factory.createType("Ljava/time/chrono/ChronoPeriod;"), chronoLocalDateType),
              "until"),
          AndroidApiLevel.O);
      // long toEpochDay()
      apiLevelConsumer.accept(
          factory.createMethod(timeType, factory.createProto(factory.longType), "toEpochDay"),
          AndroidApiLevel.O);
      // long until(java.time.temporal.Temporal, java.time.temporal.TemporalUnit)
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType,
              factory.createProto(factory.longType, temporalType, temporalUnitType),
              "until"),
          AndroidApiLevel.O);

      // java.time.chrono.Era getEra()
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType,
              factory.createProto(factory.createType("Ljava/time/chrono/Era;")),
              "getEra"),
          AndroidApiLevel.O);
      // java.time.chrono.Chronology getChronology()
      apiLevelConsumer.accept(
          factory.createMethod(
              timeType,
              factory.createProto(factory.createType("Ljava/time/chrono/Chronology;")),
              "getChronology"),
          AndroidApiLevel.O);
      DexType[] returnTypesForModificationMethods =
          new DexType[] {chronoLocalDateType, temporalType};
      for (DexType returnType : returnTypesForModificationMethods) {
        // [returnType] minus(long, java.time.temporal.TemporalUnit)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType,
                factory.createProto(returnType, factory.longType, temporalUnitType),
                "minus"),
            AndroidApiLevel.O);
        // [returnType] minus(java.time.temporal.TemporalAmount)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType, factory.createProto(returnType, temporalAmountType), "minus"),
            AndroidApiLevel.O);
        // [returnType] plus(long, java.time.temporal.TemporalUnit)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType,
                factory.createProto(returnType, factory.longType, temporalUnitType),
                "plus"),
            AndroidApiLevel.O);
        // [returnType] plus(java.time.temporal.TemporalAmount)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType, factory.createProto(returnType, temporalAmountType), "plus"),
            AndroidApiLevel.O);
        // [returnType] with(java.time.temporal.TemporalField, long)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType,
                factory.createProto(returnType, temporalFieldType, factory.longType),
                "with"),
            AndroidApiLevel.O);
        // [returnType] with(java.time.temporal.TemporalAdjuster)
        apiLevelConsumer.accept(
            factory.createMethod(
                timeType, factory.createProto(returnType, temporalAdjusterType), "with"),
            AndroidApiLevel.O);
      }
    }
    // boolean java.time.chrono.HijrahDate.isLeapYear()
    apiLevelConsumer.accept(
        factory.createMethod(
            factory.createType("Ljava/time/chrono/HijrahDate;"),
            factory.createProto(factory.booleanType),
            "isLeapYear"),
        AndroidApiLevel.O);
  }
}
