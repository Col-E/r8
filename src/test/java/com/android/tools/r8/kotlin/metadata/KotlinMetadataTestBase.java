// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public abstract class KotlinMetadataTestBase extends KotlinTestBase {

  public KotlinMetadataTestBase(KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
  }

  static final String PKG = KotlinMetadataTestBase.class.getPackage().getName();
  static final String PKG_PREFIX = DescriptorUtils.getBinaryNameFromJavaType(PKG);

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
      BiConsumer<Integer, Integer> addedStringsInspector) {
    IntBox addedStrings = new IntBox();
    IntBox addedNonInitStrings = new IntBox();
    for (FoundClassSubject clazzSubject :
        originalInspector.allClasses().stream()
            .sorted(Comparator.comparing(FoundClassSubject::getFinalName))
            .collect(Collectors.toList())) {
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

      // We cannot assert equality of the data since it may be ordered differently. However, we
      // will check for the changes to the string pool and then validate the same parsing
      // by using the KotlinMetadataWriter.
      Map<String, List<String>> descriptorToNames = new HashMap<>();
      clazzSubject.forAllMethods(
          method ->
              descriptorToNames
                  .computeIfAbsent(
                      method.getFinalSignature().toDescriptor(), ignoreArgument(ArrayList::new))
                  .add(method.getFinalName()));
      HashSet<String> originalStrings = new HashSet<>(Arrays.asList(originalHeader.getData2()));
      HashSet<String> rewrittenStrings = new HashSet<>(Arrays.asList(rewrittenHeader.getData2()));
      rewrittenStrings.forEach(
          rewrittenString -> {
            if (originalStrings.contains(rewrittenString)) {
              return;
            }
            addedStrings.increment();
            // The init is not needed by if we cannot lookup the descriptor in the table, we have
            // to emit it and that adds <init>.
            if (rewrittenString.equals("<init>")) {
              return;
            }
            // We have decided to keep invalid signatures, but they will end up in the string pool
            // when we emit them. The likely cause of them not being there in the first place seems
            // to be that they are not correctly written in the type table.
            if (rewrittenString.equals("L;") || rewrittenString.equals("(L;)V")) {
              return;
            }
            addedNonInitStrings.increment();
          });
      assertEquals(originalHeader.getPackageName(), rewrittenHeader.getPackageName());

      String expected = KotlinMetadataWriter.kotlinMetadataToString("", originalMetadata);
      String actual = KotlinMetadataWriter.kotlinMetadataToString("", rewrittenMetadata);
      assertEquals(expected, actual);
    }
    addedStringsInspector.accept(addedStrings.get(), addedNonInitStrings.get());
  }

  public static void verifyExpectedWarningsFromKotlinReflectAndStdLib(
      TestCompileResult<?, ?> compileResult) {
    compileResult.assertAllWarningMessagesMatch(
        anyOf(
            equalTo("Resource 'META-INF/MANIFEST.MF' already exists."),
            equalTo("Resource 'META-INF/versions/9/module-info.class' already exists.")));
  }
}
