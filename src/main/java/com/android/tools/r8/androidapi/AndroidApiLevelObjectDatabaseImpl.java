// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.apimodel.AndroidApiDatabaseBuilder;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class AndroidApiLevelObjectDatabaseImpl implements AndroidApiLevelDatabase {

  private final Map<DexType, AndroidApiClass> predefinedApiTypeLookup;

  private final AndroidApiClass SENTINEL =
      new AndroidApiClass(null) {

        @Override
        public AndroidApiLevel getApiLevel() {
          return null;
        }

        @Override
        public int getMemberCount() {
          return 0;
        }

        @Override
        protected TraversalContinuation visitFields(
            BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation> visitor,
            ClassReference holder,
            int minApiClass) {
          return null;
        }

        @Override
        protected TraversalContinuation visitMethods(
            BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation> visitor,
            ClassReference holder,
            int minApiClass) {
          return null;
        }
      };

  public AndroidApiLevelObjectDatabaseImpl(
      List<AndroidApiForHashingClass> predefinedApiTypeLookupForHashing) {
    Map<DexType, AndroidApiClass> predefinedMap = new HashMap<>();
    for (AndroidApiForHashingClass androidApiClass : predefinedApiTypeLookupForHashing) {
      predefinedMap.put(
          androidApiClass.getType(),
          new AndroidApiClass(androidApiClass.getType().asClassReference()) {
            @Override
            public AndroidApiLevel getApiLevel() {
              return androidApiClass.getApiLevel();
            }

            @Override
            public int getMemberCount() {
              return 0;
            }

            @Override
            protected TraversalContinuation visitFields(
                BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation> visitor,
                ClassReference holder,
                int minApiClass) {
              Box<TraversalContinuation> continuationBox =
                  new Box<>(TraversalContinuation.CONTINUE);
              androidApiClass.visitFieldsWithApiLevels(
                  (field, apiLevel) -> {
                    if (continuationBox.get().shouldContinue()) {
                      continuationBox.set(visitor.apply(field.asFieldReference(), apiLevel));
                    }
                  });
              return continuationBox.get();
            }

            @Override
            protected TraversalContinuation visitMethods(
                BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation> visitor,
                ClassReference holder,
                int minApiClass) {
              Box<TraversalContinuation> continuationBox =
                  new Box<>(TraversalContinuation.CONTINUE);
              androidApiClass.visitMethodsWithApiLevels(
                  (method, apiLevel) -> {
                    if (continuationBox.get().shouldContinue()) {
                      continuationBox.set(visitor.apply(method.asMethodReference(), apiLevel));
                    }
                  });
              return continuationBox.get();
            }
          });
    }
    this.predefinedApiTypeLookup = predefinedMap;
  }

  @Override
  public AndroidApiLevel getTypeApiLevel(DexType type) {
    return lookupDefinedApiLevel(type);
  }

  @Override
  public AndroidApiLevel getMethodApiLevel(DexMethod method) {
    return lookupDefinedApiLevel(method);
  }

  @Override
  public AndroidApiLevel getFieldApiLevel(DexField field) {
    return lookupDefinedApiLevel(field);
  }

  private AndroidApiLevel lookupDefinedApiLevel(DexReference reference) {
    AndroidApiClass foundClass =
        predefinedApiTypeLookup.getOrDefault(reference.getContextType(), SENTINEL);
    if (foundClass == null) {
      return AndroidApiLevel.UNKNOWN;
    }
    AndroidApiClass androidApiClass;
    if (foundClass == SENTINEL) {
      androidApiClass =
          AndroidApiDatabaseBuilder.buildClass(reference.getContextType().asClassReference());
      if (androidApiClass == null) {
        predefinedApiTypeLookup.put(reference.getContextType(), null);
        return AndroidApiLevel.UNKNOWN;
      }
    } else {
      androidApiClass = foundClass;
    }
    return reference.apply(
        type -> androidApiClass.getApiLevel(),
        field -> {
          FieldReference fieldReference = field.asFieldReference();
          Box<AndroidApiLevel> apiLevelBox = new Box<>(AndroidApiLevel.UNKNOWN);
          androidApiClass.visitFields(
              (fieldRef, apiLevel) -> {
                if (fieldReference.equals(fieldRef)) {
                  apiLevelBox.set(apiLevel);
                  return TraversalContinuation.BREAK;
                }
                return TraversalContinuation.CONTINUE;
              });
          return apiLevelBox.get();
        },
        method -> {
          MethodReference methodReference = method.asMethodReference();
          Box<AndroidApiLevel> apiLevelBox = new Box<>(AndroidApiLevel.UNKNOWN);
          androidApiClass.visitMethods(
              (methodRef, apiLevel) -> {
                if (methodReference.equals(methodRef)) {
                  apiLevelBox.set(apiLevel);
                  return TraversalContinuation.BREAK;
                }
                return TraversalContinuation.CONTINUE;
              });
          return apiLevelBox.get();
        });
  }
}
