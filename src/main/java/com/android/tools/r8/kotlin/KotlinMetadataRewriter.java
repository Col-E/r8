// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.INVALID_KOTLIN_INFO;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.NO_KOTLIN_INFO;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import kotlinx.metadata.jvm.KotlinClassHeader;

public class KotlinMetadataRewriter {

  private final AppView<?> appView;
  private final NamingLens lens;
  private final DexItemFactory factory;
  private final Kotlin kotlin;

  public KotlinMetadataRewriter(AppView<?> appView, NamingLens lens) {
    this.appView = appView;
    this.lens = lens;
    this.factory = appView.dexItemFactory();
    this.kotlin = factory.kotlin;
  }

  private static boolean isNotKotlinMetadata(AppView<?> appView, DexAnnotation annotation) {
    return annotation.annotation.type != appView.dexItemFactory().kotlinMetadataType;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          KotlinClassLevelInfo kotlinInfo = clazz.getKotlinInfo();
          DexAnnotation oldMeta = clazz.annotations().getFirstMatching(factory.kotlinMetadataType);
          if (kotlinInfo == INVALID_KOTLIN_INFO) {
            // Maintain invalid kotlin info for classes.
            return;
          }
          if (oldMeta == null
              || kotlinInfo == NO_KOTLIN_INFO
              || (appView.appInfo().hasLiveness()
                  && !appView.withLiveness().appInfo().isPinned(clazz.type))) {
            // Remove @Metadata in DexAnnotation when there is no kotlin info and the type is not
            // missing.
            if (oldMeta != null) {
              clazz.setAnnotations(
                  clazz.annotations().keepIf(anno -> isNotKotlinMetadata(appView, anno)));
            }
            return;
          }
          try {
            KotlinClassHeader kotlinClassHeader = kotlinInfo.rewrite(clazz, appView, lens);
            DexAnnotation newMeta =
                createKotlinMetadataAnnotation(kotlinClassHeader, kotlinInfo.getPackageName());
            clazz.setAnnotations(
                clazz.annotations().rewrite(anno -> anno == oldMeta ? newMeta : anno));
          } catch (Throwable t) {
            appView
                .options()
                .reporter
                .warning(KotlinMetadataDiagnostic.unexpectedErrorWhenRewriting(clazz.type, t));
          }
        },
        executorService);
  }

  private DexAnnotation createKotlinMetadataAnnotation(
      KotlinClassHeader header, String packageName) {
    List<DexAnnotationElement> elements = new ArrayList<>();
    elements.add(
        new DexAnnotationElement(
            kotlin.metadata.metadataVersion, createIntArray(header.getMetadataVersion())));
    elements.add(
        new DexAnnotationElement(
            kotlin.metadata.bytecodeVersion, createIntArray(header.getBytecodeVersion())));
    elements.add(
        new DexAnnotationElement(kotlin.metadata.kind, DexValueInt.create(header.getKind())));
    elements.add(
        new DexAnnotationElement(kotlin.metadata.data1, createStringArray(header.getData1())));
    elements.add(
        new DexAnnotationElement(kotlin.metadata.data2, createStringArray(header.getData2())));
    if (packageName != null && !packageName.isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.packageName, new DexValueString(factory.createString(packageName))));
    }
    if (!header.getExtraString().isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.extraString,
              new DexValueString(factory.createString(header.getExtraString()))));
    }
    if (header.getExtraInt() != 0) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.extraInt, DexValueInt.create(header.getExtraInt())));
    }
    DexEncodedAnnotation encodedAnnotation =
        new DexEncodedAnnotation(
            factory.kotlinMetadataType, elements.toArray(DexAnnotationElement.EMPTY_ARRAY));
    return new DexAnnotation(DexAnnotation.VISIBILITY_RUNTIME, encodedAnnotation);
  }

  private DexValueArray createIntArray(int[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = DexValueInt.create(data[i]);
    }
    return new DexValueArray(values);
  }

  private DexValueArray createStringArray(String[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = new DexValueString(factory.createString(data[i]));
    }
    return new DexValueArray(values);
  }
}
