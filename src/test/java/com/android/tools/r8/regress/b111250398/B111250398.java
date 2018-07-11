// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111250398;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;

// Copy of javax.inject.Provider.
interface Provider<T> {
  T get();
}

// Copy of dagger.internal.SingleClass.
final class SingleCheck<T> implements Provider<T> {
  private static final Object UNINITIALIZED = new Object();

  private volatile Provider<T> provider;
  private volatile Object instance = UNINITIALIZED;

  private SingleCheck(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the delegate provider
  @Override
  public T get() {
    Object local = instance;
    if (local == UNINITIALIZED) {
      // provider is volatile and might become null after the check, so retrieve the provider first
      Provider<T> providerReference = provider;
      if (providerReference == null) {
        // The provider was null, so the instance must already be set
        local = instance;
      } else {
        local = providerReference.get();
        instance = local;

        // Null out the reference to the provider. We are never going to need it again, so we can
        // make it eligible for GC.
        provider = null;
      }
    }
    return (T) local;
  }

  // This method is not relevant for the test.
  /*
  public static <P extends Provider<T>, T> Provider<T> provider(P provider) {
    // If a scoped @Binds delegates to a scoped binding, don't cache the value again.
    if (provider instanceof SingleCheck || provider instanceof DoubleCheck) {
      return provider;
    }
    return new SingleCheck<T>(checkNotNull(provider));
  }
  */
}

// Several field gets on non-volatile and volatile fields on the same class.
class A {
  int t;
  int f;
  static int sf;
  volatile int v;
  static volatile int sv;

  public void mf() {
    t = f;
    t = f;
    t = f;
    t = f;
    t = f;
  }

  public void msf() {
    t = sf;
    t = sf;
    t = sf;
    t = sf;
    t = sf;
  }

  public void mv() {
    t = v;
    t = v;
    t = v;
    t = v;
    t = v;
  }

  public void msv() {
    t = sv;
    t = sv;
    t = sv;
    t = sv;
    t = sv;
  }
}

// Several field gets on non-volatile and volatile fields on different class.
class B {
  int t;

  public void mf(A a) {
    t = a.f;
    t = a.f;
    t = a.f;
    t = a.f;
    t = a.f;
  }

  public void msf() {
    t = A.sf;
    t = A.sf;
    t = A.sf;
    t = A.sf;
    t = A.sf;
  }

  public void mv(A a) {
    t = a.v;
    t = a.v;
    t = a.v;
    t = a.v;
    t = a.v;
  }

  public void msv() {
    t = A.sv;
    t = A.sv;
    t = A.sv;
    t = A.sv;
    t = A.sv;
  }
}

// Modified sample from http://tutorials.jenkov.com/java-concurrency/volatile.html.
class C {
  private int years;
  private int months;
  private volatile int days;

  public int totalDays() {
    int total = this.days;
    total += months * 30;
    total += years * 365;
    return total;
  }

  public int totalDaysTimes2() {
    int total = this.days;
    total += months * 30;
    total += years * 365;
    total += this.days;
    total += months * 30;
    total += years * 365;
    return total;
  }

  public int totalDaysTimes3() {
    int total = this.days;
    total += months * 30;
    total += years * 365;
    total += this.days;
    total += months * 30;
    total += years * 365;
    total += this.days;
    total += months * 30;
    total += years * 365;
    return total;
  }

  public void update(int years, int months, int days){
    this.years  = years;
    this.months = months;
    this.days   = days;
  }
}

public class B111250398 extends TestBase {

  private void releaseMode(InternalOptions options) {
    options.debug = false;
  }

  private long countIget(DexCode code, DexField field) {
    return Arrays.stream(code.instructions)
        .filter(instruction -> instruction instanceof Iget)
        .map(instruction -> (Iget) instruction)
        .filter(get -> get.getField() == field)
        .count();
  }

  private long countSget(DexCode code, DexField field) {
    return Arrays.stream(code.instructions)
        .filter(instruction -> instruction instanceof Sget)
        .map(instruction -> (Sget) instruction)
        .filter(get -> get.getField() == field)
        .count();
  }

  private long countIgetObject(MethodSubject method, FieldSubject field) {
    return Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .filter(instruction -> instruction instanceof IgetObject)
        .map(instruction -> (IgetObject) instruction)
        .filter(get -> get.getField() == field.getField().field)
        .count();
  }

  private void check(DexInspector inspector, int mfOnBGets, int msfOnBGets) {
    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());
    MethodSubject mfOnA = classA.method("void", "mf", ImmutableList.of());
    assertThat(mfOnA, isPresent());
    MethodSubject msfOnA = classA.method("void", "msf", ImmutableList.of());
    assertThat(msfOnA, isPresent());
    MethodSubject mvOnA = classA.method("void", "mv", ImmutableList.of());
    assertThat(mvOnA, isPresent());
    MethodSubject msvOnA = classA.method("void", "msv", ImmutableList.of());
    assertThat(msvOnA, isPresent());
    FieldSubject fOnA = classA.field("int", "f");
    assertThat(fOnA, isPresent());
    FieldSubject sfOnA = classA.field("int", "sf");
    assertThat(sfOnA, isPresent());
    FieldSubject vOnA = classA.field("int", "v");
    assertThat(vOnA, isPresent());
    FieldSubject svOnA = classA.field("int", "sv");
    assertThat(svOnA, isPresent());
    ClassSubject classB = inspector.clazz(B.class);
    assertThat(classB, isPresent());
    MethodSubject mfOnB = classB.method("void", "mf", ImmutableList.of(classA.getOriginalName()));
    assertThat(mfOnB, isPresent());
    MethodSubject msfOnB = classB.method("void", "msf", ImmutableList.of());
    assertThat(msfOnB, isPresent());
    MethodSubject mvOnB = classB.method("void", "mv", ImmutableList.of(classA.getOriginalName()));
    assertThat(mvOnB, isPresent());
    MethodSubject msvOnB = classB.method("void", "msv", ImmutableList.of());
    assertThat(msvOnB, isPresent());
    // Field load of volatile fields are never eliminated.
    assertEquals(5, countIget(mvOnA.getMethod().getCode().asDexCode(), vOnA.getField().field));
    assertEquals(5, countSget(msvOnA.getMethod().getCode().asDexCode(), svOnA.getField().field));
    assertEquals(5, countIget(mvOnB.getMethod().getCode().asDexCode(), vOnA.getField().field));
    assertEquals(5, countSget(msvOnB.getMethod().getCode().asDexCode(), svOnA.getField().field));
    // For fields on the same class both separate compilation (D8) and whole program
    // compilation (R8) will eliminate field loads on non-volatile fields.
    assertEquals(1, countIget(mfOnA.getMethod().getCode().asDexCode(), fOnA.getField().field));
    assertEquals(1, countSget(msfOnA.getMethod().getCode().asDexCode(), sfOnA.getField().field));
    // For fields on other class both separate compilation (D8) and whole program
    // compilation (R8) will differ in the eliminated field loads of non-volatile fields.
    assertEquals(mfOnBGets,
        countIget(mfOnB.getMethod().getCode().asDexCode(), fOnA.getField().field));
    assertEquals(msfOnBGets,
        countSget(msfOnB.getMethod().getCode().asDexCode(), sfOnA.getField().field));
  }

  @Test
  public void testSeparateCompilation() throws Exception {
    DexInspector inspector =
        new DexInspector(compileWithD8(readClasses(A.class, B.class), this::releaseMode));
    check(inspector, 5, 5);
  }

  @Test
  public void testWholeProgram() throws Exception {
    DexInspector inspector =
        new DexInspector(compileWithR8(readClasses(A.class, B.class), this::releaseMode));
    // The reason for getting two Igets in B.mf is that the first Iget inserts a NonNull
    // instruction which creates a new value for the remaining Igets.
    check(inspector, 2, 1);
  }

  private void checkMixed(AndroidApp app) throws Exception{
    DexInspector inspector = new DexInspector(app);
    ClassSubject classC = inspector.clazz(C.class);
    assertThat(classC, isPresent());
    MethodSubject totalDays = classC.method("int", "totalDays", ImmutableList.of());
    assertThat(totalDays, isPresent());
    MethodSubject totalDaysTimes2 = classC.method("int", "totalDaysTimes2", ImmutableList.of());
    assertThat(totalDaysTimes2, isPresent());
    MethodSubject totalDaysTimes3 = classC.method("int", "totalDaysTimes3", ImmutableList.of());
    assertThat(totalDaysTimes3, isPresent());
    FieldSubject years = classC.field("int", "years");
    assertThat(years, isPresent());
    FieldSubject months = classC.field("int", "months");
    assertThat(months, isPresent());
    FieldSubject days = classC.field("int", "days");
    assertThat(days, isPresent());


    for (FieldSubject field : new FieldSubject[]{years, months, days}) {
      assertEquals(1,
          countIget(totalDays.getMethod().getCode().asDexCode(), field.getField().field));
      assertEquals(2,
          countIget(totalDaysTimes2.getMethod().getCode().asDexCode(), field.getField().field));
      assertEquals(3,
          countIget(totalDaysTimes3.getMethod().getCode().asDexCode(), field.getField().field));
    }
  }

  @Test
  public void testMixedVolatileNonVolatile() throws Exception {
    AndroidApp app = readClasses(C.class);
    checkMixed(compileWithD8(app, this::releaseMode));
    checkMixed(compileWithR8(app, this::releaseMode));
  }

  private void checkDaggerSingleProviderGet(AndroidApp app) throws Exception {
    DexInspector inspector = new DexInspector(app);
    MethodSubject get =
        inspector.clazz(SingleCheck.class).method("java.lang.Object", "get", ImmutableList.of());
    assertThat(get, isPresent());
    FieldSubject instance =
        inspector.clazz(SingleCheck.class).field("java.lang.Object", "instance");
    assertEquals(2, countIgetObject(get, instance));
  }

  @Test
  public void testDaggerSingleProvider() throws Exception {
    AndroidApp app = readClasses(Provider.class, SingleCheck.class);
    checkDaggerSingleProviderGet(compileWithD8(app, this::releaseMode));
    checkDaggerSingleProviderGet(compileWithR8(app, this::releaseMode));
  }
}
