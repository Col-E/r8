// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmContractVisitor;
import kotlinx.metadata.KmEffectExpressionVisitor;
import kotlinx.metadata.KmEffectInvocationKind;
import kotlinx.metadata.KmEffectType;
import kotlinx.metadata.KmEffectVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmLambdaVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.KmTypeAliasVisitor;
import kotlinx.metadata.KmTypeParameterVisitor;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.KmValueParameterVisitor;
import kotlinx.metadata.KmVariance;
import kotlinx.metadata.KmVersionRequirementVisitor;

/**
 * The reason for having these visitor providers is to make the separation of concern a bit easier
 * while also working with the kotlinx.metadata visitors as shown by the following example:
 *
 * <p>Say we have the following structure KotlinTypeInfo: { TypeProjects:
 * [KotlinTypeProjectionInfo(StarProjection)] }
 *
 * <p>Now the KmTypeVisitor (we use to generate the KotlinTypeInfo, has a visitProjection(int flags,
 * KmVariance variance) generator that will return a new KmTypeVisitor, however, if the projection
 * is a star projection, the generator visitStarProjection() should be used.
 *
 * <p>The information about the projection being a star projection is contained in the
 * KotlinTypeProjectionInfo. As a result, KotlinTypeInfo should query the object for picking the
 * right generator, the KotlinTypeProjectionInfo should return a KmTypeProjection object, or we
 * simply capture the generators lazily (by these providers), such that the object with all the
 * information can decide when/what object to create.
 *
 * <p>Another benefit of this approach than using the build in visitors is that shared structures,
 * such as KotlinAnnotationInfo that can be on type-aliases, functions and properties will not have
 * to take in three different type of visitors.
 */
public class KmVisitorProviders {

  @FunctionalInterface
  public interface KmAnnotationVisitorProvider {

    void get(KmAnnotation annotation);
  }

  @FunctionalInterface
  public interface KmFunctionVisitorProvider {

    KmFunctionVisitor get(int flags, String name);
  }

  public interface KmLambdaVisitorProvider {

    KmLambdaVisitor get();
  }

  @FunctionalInterface
  public interface KmPropertyVisitorProvider {

    KmPropertyVisitor get(int flags, String name, int getterFlags, int setterFlags);
  }

  @FunctionalInterface
  public interface KmTypeAliasVisitorProvider {

    KmTypeAliasVisitor get(int flags, String name);
  }

  @FunctionalInterface
  public interface KmTypeParameterVisitorProvider {

    KmTypeParameterVisitor get(int flags, String name, int id, KmVariance variance);
  }

  @FunctionalInterface
  public interface KmTypeProjectionVisitorProvider {

    KmTypeVisitor get(int flags, KmVariance variance);
  }

  @FunctionalInterface
  public interface KmTypeStarProjectionVisitorProvider {

    void get();
  }

  @FunctionalInterface
  public interface KmTypeVisitorProvider {

    KmTypeVisitor get(int flags);
  }

  @FunctionalInterface
  public interface KmValueParameterVisitorProvider {

    KmValueParameterVisitor get(int flags, String name);
  }

  @FunctionalInterface
  public interface KmFlexibleUpperBoundVisitorProvider {

    KmTypeVisitor get(int flags, String typeFlexibilityId);
  }

  @FunctionalInterface
  public interface KmVersionRequirementVisitorProvider {

    KmVersionRequirementVisitor get();
  }

  @FunctionalInterface
  public interface KmContractVisitorProvider {

    KmContractVisitor get();
  }

  @FunctionalInterface
  public interface KmEffectVisitorProvider {

    KmEffectVisitor get(KmEffectType type, KmEffectInvocationKind effectInvocationKind);
  }

  @FunctionalInterface
  public interface KmEffectExpressionVisitorProvider {

    KmEffectExpressionVisitor get();
  }
}
