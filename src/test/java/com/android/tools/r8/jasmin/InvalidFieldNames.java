// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.Adler32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidFieldNames extends JasminTestBase {

  private static final String CLASS_NAME = "Test";
  private static String FIELD_VALUE = "42";

  @Parameters(name = "\"{0}\", jvm: {1}, art: {2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {new TestStringParameter("azAZ09$_"), true, true},
          {new TestStringParameter("_"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("a-b"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\u00a0"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\u00a1"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\u1fff"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\u2000"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\u200f"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\u2010"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\u2027"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\u2028"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\u202f"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\u2030"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\ud7ff"), !ToolHelper.isJava9Runtime(), true},

          // Standalone high and low surrogates.
          {new TestStringParameter("\ud800"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\udbff"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\udc00"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\udfff"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\ue000"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\uffef"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\ufff0"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("\uffff"), !ToolHelper.isJava9Runtime(), false},

          // Single and double code points above 0x10000.
          {new TestStringParameter("\ud800\udc00"), true, true},
          {new TestStringParameter("\ud800\udcfa"), true, true},
          {new TestStringParameter("\ud800\udcfb"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\udbff\udfff"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("\ud800\udc00\ud800\udcfa"), true, true},
          {new TestStringParameter("\ud800\udc00\udbff\udfff"), !ToolHelper.isJava9Runtime(), true},
          {new TestStringParameter("a/b"), false, false},
          {new TestStringParameter("<a"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("a>"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("a<b>"), !ToolHelper.isJava9Runtime(), false},
          {new TestStringParameter("<a>b"), !ToolHelper.isJava9Runtime(), false}
        });
  }

  // TestStringParameter is a String with modified toString() which prints \\uXXXX for
  // characters outside 0x20..0x7e.
  static class TestStringParameter {
    private final String value;

    TestStringParameter(String value) {
      this.value = value;
    }

    String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return StringUtils.toASCIIString(value);
    }
  }

  private String name;
  private boolean validForJVM;
  private boolean validForArt;

  public InvalidFieldNames(TestStringParameter name, boolean validForJVM, boolean validForArt) {
    this.name = name.getValue();
    this.validForJVM = validForJVM;
    this.validForArt = validForArt;
  }

  private byte[] trimLastZeroByte(byte[] bytes) {
    assert bytes.length > 0 && bytes[bytes.length - 1] == 0;
    byte[] result = new byte[bytes.length - 1];
    System.arraycopy(bytes, 0, result, 0, result.length);
    return result;
  }

  private JasminBuilder createJasminBuilder() {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(CLASS_NAME);

    clazz.addStaticField(name, "I", FIELD_VALUE);

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic " + CLASS_NAME + "/" + name + " I",
        "  invokevirtual java/io/PrintStream.print(I)V",
        "  return");
    return builder;
  }

  private AndroidApp createAppWithSmali() throws Exception {

    SmaliBuilder smaliBuilder = new SmaliBuilder(CLASS_NAME);
    String originalSourceFile = CLASS_NAME + FileUtils.JAVA_EXTENSION;
    smaliBuilder.setSourceFile(originalSourceFile);

    // We're using a valid placeholder string which will be replaced by the actual name.
    byte[] nameMutf8 = trimLastZeroByte(DexString.encodeToMutf8(name));

    String placeholderString = Strings.repeat("A", nameMutf8.length);

    smaliBuilder.addStaticField(placeholderString, "I", FIELD_VALUE);
    MethodSignature mainSignature =
        smaliBuilder.addMainMethod(
            1,
            "sget-object p0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
            "sget v0, LTest;->" + placeholderString + ":I",
            "invoke-virtual {p0, v0}, Ljava/io/PrintStream;->print(I)V",
            "return-void");
    byte[] dexCode = smaliBuilder.compile();

    // Replace placeholder by mutf8-encoded name
    byte[] placeholderBytes = trimLastZeroByte(DexString.encodeToMutf8(placeholderString));
    assert placeholderBytes.length == nameMutf8.length;
    int index = Bytes.indexOf(dexCode, placeholderBytes);
    if (index >= 0) {
      System.arraycopy(nameMutf8, 0, dexCode, index, nameMutf8.length);
    }
    assert Bytes.indexOf(dexCode, placeholderBytes) < 0;

    // Update checksum
    Adler32 adler = new Adler32();
    adler.update(dexCode, Constants.SIGNATURE_OFFSET, dexCode.length - Constants.SIGNATURE_OFFSET);
    int checksum = (int) adler.getValue();
    for (int i = 0; i < 4; ++i) {
      dexCode[Constants.CHECKSUM_OFFSET + i] = (byte) (checksum >> (8 * i) & 0xff);
    }

    return AndroidApp.builder()
        .addDexProgramData(dexCode, new PathOrigin(Paths.get(originalSourceFile)))
        .build();
  }

  @Test
  public void invalidFieldNames() throws Exception {
    JasminBuilder jasminBuilder = createJasminBuilder();

    if (validForJVM) {
      String javaResult = runOnJava(jasminBuilder, CLASS_NAME);
      assertEquals(FIELD_VALUE, javaResult);
    } else {
      try {
        runOnJava(jasminBuilder, CLASS_NAME);
        fail("Should have failed on JVM.");
      } catch (AssertionError e) {
        // Silent on expected failure.
      }
    }

    if (validForArt) {
      String artResult = runOnArtD8(jasminBuilder, CLASS_NAME);
      assertEquals(FIELD_VALUE, artResult);
    } else {
      // Make sure the compiler fails.
      try {
        runOnArtD8(jasminBuilder, CLASS_NAME);
        fail("D8 should have rejected this case.");
      } catch (CompilationError t) {
        assertTrue(t.getMessage().contains(name));
      }

      // Make sure ART also fail, if D8 rejects it.
      try {
        runOnArt(createAppWithSmali(), CLASS_NAME);
        fail("Art should have failed.");
      } catch (AssertionError e) {
        // Silent on expected failure.
      }
    }
  }
}
