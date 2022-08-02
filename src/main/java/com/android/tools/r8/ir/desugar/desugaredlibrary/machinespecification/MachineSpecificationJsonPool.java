// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

public class MachineSpecificationJsonPool {

  static final String REQUIRED_COMPILATION_API_LEVEL_KEY = "required_compilation_api_level";
  static final String SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY =
      "synthesized_library_classes_package_prefix";
  static final String IDENTIFIER_KEY = "identifier";
  static final String SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY = "support_all_callbacks_from_library";
  static final String SHRINKER_CONFIG_KEY = "shrinker_config";

  static final String COMMON_FLAGS_KEY = "common_flags";
  static final String LIBRARY_FLAGS_KEY = "library_flags";
  static final String PROGRAM_FLAGS_KEY = "program_flags";

  static final String API_LEVEL_BELOW_OR_EQUAL_KEY = "api_level_below_or_equal";
  static final String API_LEVEL_GREATER_OR_EQUAL_KEY = "api_level_greater_or_equal";

  static final String REWRITE_TYPE_KEY = "rewrite_type";
  static final String MAINTAIN_TYPE_KEY = "maintain_type";
  static final String REWRITE_DERIVED_TYPE_ONLY_KEY = "rewrite_derived_type_only";
  static final String STATIC_FIELD_RETARGET_KEY = "static_field_retarget";
  static final String COVARIANT_RETARGET_KEY = "covariant_retarget";
  static final String STATIC_RETARGET_KEY = "static_retarget";
  static final String NON_EMULATED_VIRTUAL_RETARGET_KEY = "non_emulated_virtual_retarget";
  static final String EMULATED_VIRTUAL_RETARGET_KEY = "emulated_virtual_retarget";
  static final String EMULATED_VIRTUAL_RETARGET_THROUGH_EMULATED_INTERFACE_KEY =
      "emulated_virtual_retarget_through_emulated_interface";
  static final String API_GENERIC_TYPES_CONVERSION_KEY = "api_generic_types_conversion";
  static final String EMULATED_INTERFACE_KEY = "emulated_interface";
  static final String WRAPPER_KEY = "wrapper";
  static final String LEGACY_BACKPORT_KEY = "legacy_backport";
  static final String DONT_RETARGET_KEY = "dont_retarget";
  static final String CUSTOM_CONVERSION_KEY = "custom_conversion";
  static final String AMEND_LIBRARY_METHOD_KEY = "amend_library_method";
  static final String AMEND_LIBRARY_FIELD_KEY = "amend_library_field";

  static final String PACKAGE_MAP_KEY = "package_map";
}
