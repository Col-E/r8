// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.invokesuper2.C0;
import com.android.tools.r8.graph.invokesuper2.C1;
import com.android.tools.r8.graph.invokesuper2.C2;
import com.android.tools.r8.graph.invokesuper2.I0;
import com.android.tools.r8.graph.invokesuper2.I1;
import com.android.tools.r8.graph.invokesuper2.I2;
import com.android.tools.r8.graph.invokesuper2.I3;
import com.android.tools.r8.graph.invokesuper2.I4;
import com.android.tools.r8.graph.invokesuper2.Main;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

public class TargetLookupTest extends SmaliTestBase {

  @Test
  public void lookupDirect() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addDefaultConstructor();

    builder.addMethodRaw(
        "  .method private static x()I",
        "    .locals 1",
        "    const v0, 0",
        "    return v0",
        "  .end method"
    );

    // Instance method invoking static method using invoke-direct. This does not run on Art, but
    // results in an IncompatibleClassChangeError.
    builder.addMethodRaw(
        "  .method public y()I",
        "    .locals 1",
        "    invoke-direct       {p0}, " + builder.getCurrentClassDescriptor() + "->x()I",
        "    move-result         v0",
        "    return              v0",
        "  .end method"
    );

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, LTest;",
        "    invoke-direct       {v1}, " + builder.getCurrentClassDescriptor() + "-><init>()V",
        "    :try_start",
        "    invoke-virtual      {v1}, " + builder.getCurrentClassDescriptor() + "->y()I",
        "    :try_end",
        "    const-string        v1, \"ERROR\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    :return",
        "    return-void",
        "    .catch Ljava/lang/IncompatibleClassChangeError; {:try_start .. :try_end} :catch",
        "    :catch",
        "    const-string        v1, \"OK\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    goto :return"
    );

    AndroidApp application = buildApplication(builder);
    AppInfo appInfo = getAppInfo(application);
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod method = getMethod(inspector, DEFAULT_CLASS_NAME, "int", "x",
        ImmutableList.of());
    assertNull(appInfo.lookupVirtualTarget(method.method.holder, method.method));
    assertNull(appInfo.lookupDirectTarget(method.method));
    assertNotNull(appInfo.lookupStaticTarget(method.method));

    if (ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(DexVm.Version.V4_4_4)) {
      // Dalvik rejects at verification time instead of producing the
      // expected IncompatibleClassChangeError.
      try {
        runArt(application);
      } catch (AssertionError e) {
        assert e.toString().contains("VerifyError");
      }
    } else {
      assertEquals("OK", runArt(application));
    }
  }

  @Test
  public void lookupDirectSuper() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("TestSuper");

    builder.addDefaultConstructor();

    builder.addMethodRaw(
        "  .method private static x()I",
        "    .locals 1",
        "    const               v0, 0",
        "    return              v0",
        "  .end method"
    );

    builder.addClass("Test", "TestSuper");

    builder.addDefaultConstructor();

    // Instance method invoking static method in superclass using invoke-direct. This does not run
    // on Art, but results in an IncompatibleClassChangeError.
    builder.addMethodRaw(
        "  .method public y()I",
        "    .locals 1",
        "    invoke-direct       {p0}, " + builder.getCurrentClassDescriptor() + "->x()I",
        "    move-result         v0",
        "    return              v0",
        "  .end method"
    );

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    new-instance        v1, LTest;",
        "    invoke-direct       {v1}, " + builder.getCurrentClassDescriptor() + "-><init>()V",
        "    :try_start",
        "    invoke-virtual      {v1}, " + builder.getCurrentClassDescriptor() + "->y()I",
        "    :try_end",
        "    const-string        v1, \"ERROR\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    :return",
        "    return-void",
        "    .catch Ljava/lang/IncompatibleClassChangeError; {:try_start .. :try_end} :catch",
        "    :catch",
        "    const-string        v1, \"OK\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/String;)V",
        "    goto :return"
    );

    AndroidApp application = buildApplication(builder);
    AppInfo appInfo = getAppInfo(application);
    DexInspector inspector = new DexInspector(appInfo.app);

    DexMethod methodXOnTestSuper =
        getMethod(inspector, "TestSuper", "int", "x", ImmutableList.of()).method;
    DexMethod methodYOnTest =
        getMethod(inspector, "Test", "int", "y", ImmutableList.of()).method;

    DexType classTestSuper = methodXOnTestSuper.getHolder();
    DexType classTest = methodYOnTest.getHolder();
    DexProto methodXProto = methodXOnTestSuper.proto;
    DexString methodXName = methodXOnTestSuper.name;
    DexMethod methodXOnTest =
        appInfo.dexItemFactory.createMethod(classTest, methodXProto, methodXName);

    assertNull(appInfo.lookupVirtualTarget(classTestSuper, methodXOnTestSuper));
    assertNull(appInfo.lookupVirtualTarget(classTest, methodXOnTestSuper));
    assertNull(appInfo.lookupVirtualTarget(classTest, methodXOnTest));

    assertNull(appInfo.lookupDirectTarget(methodXOnTestSuper));
    assertNull(appInfo.lookupDirectTarget(methodXOnTest));

    assertNotNull(appInfo.lookupStaticTarget(methodXOnTestSuper));
    assertNotNull(appInfo.lookupStaticTarget(methodXOnTest));

    assertEquals("OK", runArt(application));
  }

  @Test
  public void lookupFieldWithDefaultInInterface() {
    SmaliBuilder builder = new SmaliBuilder();

    builder.addInterface("Interface");
    builder.addStaticField("aField", "I", "42");

    builder.addClass("SuperClass");
    builder.addStaticField("aField", "I", "123");

    builder.addClass("SubClass", "SuperClass", Collections.singletonList("Interface"));

    builder.addClass(DEFAULT_CLASS_NAME);
    builder.addMainMethod(2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    sget                v1, LSubClass;->aField:I",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    AndroidApp application = buildApplication(builder);
    AppInfo appInfo = getAppInfo(application);
    DexItemFactory factory = appInfo.dexItemFactory;

    DexField aFieldOnSubClass = factory
        .createField(factory.createType("LSubClass;"), factory.intType, "aField");
    DexField aFieldOnInterface = factory
        .createField(factory.createType("LInterface;"), factory.intType, "aField");

    assertEquals(aFieldOnInterface,
        appInfo.lookupStaticTarget(aFieldOnSubClass.getHolder(), aFieldOnSubClass).field);

    assertEquals("42", runArt(application));

    AndroidApp processedApp = processApplication(application);
    assertEquals("42", runArt(processedApp));
  }

  @Test
  public void testLookupSuperTarget() throws Exception {
    String pkg = Main.class.getPackage().getName().replace('.', '/');

    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class clazz : new Class[]{
        I0.class, I1.class, I2.class, I3.class, I4.class,
        C0.class, C1.class, C2.class,
        Main.class}) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
      // At least java.lang.Object is needed as interface method lookup have special handling
      // of methods on java.lang.Object.
      builder.addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    }
    AndroidApp application = builder.build();
    AppInfo appInfo = getAppInfo(application);
    DexItemFactory factory = appInfo.dexItemFactory;

    DexType i0 = factory.createType("L" + pkg + "/I0;");
    DexType i1 = factory.createType("L" + pkg + "/I1;");
    DexType i2 = factory.createType("L" + pkg + "/I2;");
    DexType i3 = factory.createType("L" + pkg + "/I3;");
    DexType i4 = factory.createType("L" + pkg + "/I4;");
    DexType c0 = factory.createType("L" + pkg + "/C0;");
    DexType c1 = factory.createType("L" + pkg + "/C1;");
    DexType c2 = factory.createType("L" + pkg + "/C2;");

    DexProto mProto = factory.createProto(factory.intType);
    DexString m = factory.createString("m");
    DexMethod mOnC0 = factory.createMethod(c0, mProto, m);
    DexMethod mOnC1 = factory.createMethod(c1, mProto, m);
    DexMethod mOnI0 = factory.createMethod(i0, mProto, m);
    DexMethod mOnI1 = factory.createMethod(i1, mProto, m);
    DexMethod mOnI2 = factory.createMethod(i2, mProto, m);
    DexMethod mOnI3 = factory.createMethod(i3, mProto, m);
    DexMethod mOnI4 = factory.createMethod(i4, mProto, m);

    assertEquals(mOnI0, appInfo.lookupSuperTarget(mOnC0, c1).method);
    assertEquals(mOnI1, appInfo.lookupSuperTarget(mOnI1, c1).method);
    assertEquals(mOnI2, appInfo.lookupSuperTarget(mOnI2, c1).method);

    assertEquals(mOnI0, appInfo.lookupSuperTarget(mOnC1, c2).method);
    assertEquals(mOnI1, appInfo.lookupSuperTarget(mOnI3, c2).method);
    assertEquals(mOnI2, appInfo.lookupSuperTarget(mOnI4, c2).method);

    // Copy classes to run on the Java VM.
    Path out = temp.newFolder().toPath();
    copyTestClasses(out, I0.class, I1.class, I2.class, I3.class, I4.class);
    copyTestClasses(out, C0.class, C1.class, C2.class, Main.class);
    ProcessResult result = ToolHelper.runJava(out, Main.class.getCanonicalName());
    assertEquals(0, result.exitCode);

    // Process the application and expect the same result on Art.
    AndroidApp processedApp = processApplication(application);
    assertEquals(result.stdout, runArt(processedApp, Main.class.getCanonicalName()));
  }
}



