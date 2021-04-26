// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.IntConsumer;
import junit.framework.TestCase;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public abstract class KotlinMetadataTestBase extends KotlinTestBase {

  public KotlinMetadataTestBase(KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
  }

  static final String PKG = KotlinMetadataTestBase.class.getPackage().getName();
  static final String PKG_PREFIX = DescriptorUtils.getBinaryNameFromJavaType(PKG);

  static final String KT_ANY = "Lkotlin/Any;";
  static final String KT_ARRAY = "Lkotlin/Array;";
  static final String KT_CHAR_SEQUENCE = "Lkotlin/CharSequence;";
  static final String KT_STRING = "Lkotlin/String;";
  static final String KT_LONG = "Lkotlin/Long;";
  static final String KT_LONG_ARRAY = "Lkotlin/LongArray;";
  static final String KT_MAP = "Lkotlin/collections/Map;";
  static final String KT_UNIT = "Lkotlin/Unit;";

  static final String KT_FUNCTION1 = "Lkotlin/Function1;";
  static final String KT_COMPARABLE = "Lkotlin/Comparable;";

  public void assertEqualMetadata(
      CodeInspector originalInspector,
      CodeInspector rewrittenInspector,
      IntConsumer addedStringsInspector) {
    IntBox addedStrings = new IntBox();
    for (FoundClassSubject clazzSubject : originalInspector.allClasses()) {
      ClassSubject r8Clazz = rewrittenInspector.clazz(clazzSubject.getOriginalName());
      assertThat(r8Clazz, isPresent());
      KotlinClassMetadata originalMetadata = clazzSubject.getKotlinClassMetadata();
      KotlinClassMetadata rewrittenMetadata = r8Clazz.getKotlinClassMetadata();
      if (originalMetadata == null) {
        assertNull(rewrittenMetadata);
        continue;
      }
      assertNotNull(rewrittenMetadata);
      KotlinClassHeader originalHeader = originalMetadata.getHeader();
      KotlinClassHeader rewrittenHeader = rewrittenMetadata.getHeader();
      TestCase.assertEquals(originalHeader.getKind(), rewrittenHeader.getKind());
      // TODO(b/154199572): Should we check for meta-data version?
      TestCase.assertEquals(originalHeader.getPackageName(), rewrittenHeader.getPackageName());

      HashSet<String> originalStrings = new HashSet<>(Arrays.asList(originalHeader.getData2()));
      HashSet<String> rewrittenStrings = new HashSet<>(Arrays.asList(rewrittenHeader.getData2()));
      rewrittenStrings.forEach(
          rewrittenString -> {
            if (originalStrings.contains(rewrittenString)) {
              return;
            }
            addedStrings.increment();
          });
      // We cannot assert equality of the data since it may be ordered differently. Instead we use
      // the KotlinMetadataWriter.
      String expected = KotlinMetadataWriter.kotlinMetadataToString("", originalMetadata);
      String actual = KotlinMetadataWriter.kotlinMetadataToString("", rewrittenMetadata);
      TestCase.assertEquals(expected, actual);
    }
    addedStringsInspector.accept(addedStrings.get());
  }

  public static void verifyExpectedWarningsFromKotlinReflectAndStdLib(
      TestCompileResult<?, ?> compileResult) {
    compileResult.assertAllWarningMessagesMatch(
        anyOf(
            equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
            equalTo("Resource 'META-INF/versions/9/module-info.class' already exists.")));
  }
}
