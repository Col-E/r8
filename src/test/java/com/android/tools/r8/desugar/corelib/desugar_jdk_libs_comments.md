# Description of the core library configuration file

## Version

The first field `configuration_format_version` encodes a versioning number internal to R8/D8
in the form of an unsigned integer. It allows R8/D8 to know if the file given is supported.
Non-backward compatible changes to the desugared library increase the version number, and such
library cannot be compiled without upgrading R8/D8 to the latest version.

The second field `version` holds the version of the content for the configuration. This number
must be updated each time the configuration is changed.

## Required compilation API level

The third field `required_compilation_api_level` encodes the minimal Android API level required for
the desugared library to be compiled correctly. If the API of library used for compilation of the
library or a program using the library is lower than this level, one has to upgrade the SDK version
used to be able to use desugared libraries.

## Library and program flags

The fourth and last fields are `library_flags` and `program_flags`. They include the set of flags
required for respectively the library and the program using the desugared library compilation. The
sets of flags are different depending on the min API level used. The flags are in a list, where
each list entry specifies up to which min API level the set of flags should be applied. During
compilation, R8/D8 adds up all the required flags for the min API level specified at compilation.

For example, let's say the `program_flags` have entries for `api_level_below_or_equal` 20, 24 and 26.
If compiling the program for min API 24, R8/D8 will use both the set of flags for API 24 and 26
(24 <= 24, 24 <= 26 but !(24 <= 20)).

## Copyright

Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
for details. All rights reserved. Use of this source code is governed by a
BSD-style license that can be found in the LICENSE file.