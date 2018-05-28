// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
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
}



