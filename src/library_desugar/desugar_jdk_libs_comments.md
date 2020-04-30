# Description of the desugared library configuration file

## Version

The first field `configuration_format_version` encodes a versioning number internal to R8/D8
in the form of an unsigned integer. It allows R8/D8 to know if the file given is supported.
Non-backward compatible changes to the desugared library increase the version number, and such
library cannot be compiled without upgrading R8/D8 to the latest version.

The fields `group_id` and `artifact_id` are maven-coordinated ids for the desugared library
configuration file.

The field `version` holds the version of the content for the configuration. This number
must be updated each time the configuration is changed.

A unique identifier is generated for the desugared library configuration using
`group_id:artifact_id:version`.

## Required compilation API level

The field `required_compilation_api_level` encodes the minimal Android API level required for
the desugared library to be compiled correctly. If the API of library used for compilation of the
library or a program using the library is lower than this level, one has to upgrade the SDK version
used to be able to use desugared libraries.

## Library and program flags

The fields `library_flags` and `program_flags` include the set of flags required for respectively
the library and the program using the desugared library compilation. The sets of flags are
different depending on the min API level used. The flags are in a list, where each list entry
specifies up to which min API level the set of flags should be applied. During compilation,
R8/D8 adds up all the required flags for the min API level specified at compilation.

For example, let's say the `program_flags` have entries for `api_level_below_or_equal` 20, 24 and
26. If compiling the program for min API 24, R8/D8 will use both the set of flags for API 24 and
26 (24 <= 24, 24 <= 26 but !(24 <= 20)).

## Extra keep rules

The last field is `extra_keep_rules`, it includes keep rules that are appended by L8 when shrinking
the desugared library. It includes keep rules related to reflection inside the desugared library,
related to enum to have EnumSet working and to keep the j$ prefix.

## Copyright

Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
for details. All rights reserved. Use of this source code is governed by a
BSD-style license that can be found in the LICENSE file.