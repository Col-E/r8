#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
from __future__ import print_function
import os
import sys
import zipfile


# Proguard lookup program classes before library classes. In R8 this is
# not the behaviour (it used to be) R8 will check library classes before
# program classes. Some apps have duplicate classes in the library and program.
# To make these apps work with R8 simulate program classes before library
# classes by creating a new library jar which have all the provided library
# classes which are not also in program classes.
def SanitizeLibrariesInPgconf(sanitized_lib_path,
                              sanitized_pgconf_path,
                              pgconfs,
                              injars=None,
                              libraryjars=None):
    with open(sanitized_pgconf_path, 'w') as sanitized_pgconf:
        injars = [] if injars is None else injars
        libraryjars = [] if libraryjars is None else libraryjars
        for pgconf in pgconfs:
            pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
            first_library_jar = True
            with open(pgconf) as pgconf_file:
                for line in pgconf_file:
                    trimmed = line.strip()
                    if trimmed.startswith('-injars'):
                        # Collect -injars and leave them in the configuration.
                        injar = os.path.join(pgconf_dirname,
                                             trimmed[len('-injars'):].strip())
                        injars.append(injar)
                        sanitized_pgconf.write('-injars {}\n'.format(injar))
                    elif trimmed.startswith('-libraryjars'):
                        # Collect -libraryjars and replace them with the sanitized library.
                        libraryjar = os.path.join(
                            pgconf_dirname,
                            trimmed[len('-libraryjars'):].strip())
                        libraryjars.append(libraryjar)
                        if first_library_jar:
                            sanitized_pgconf.write(
                                '-libraryjars {}\n'.format(sanitized_lib_path))
                            first_library_jar = False
                        sanitized_pgconf.write('# {}'.format(line))
                    else:
                        sanitized_pgconf.write(line)

    SanitizeLibraries(sanitized_lib_path, libraryjars, injars)


def SanitizeLibraries(sanitized_lib_path, libraryjars, injars):
    program_entries = set()
    library_entries = set()

    for injar in injars:
        with zipfile.ZipFile(injar, 'r') as injar_zf:
            for zipinfo in injar_zf.infolist():
                program_entries.add(zipinfo.filename)

    with zipfile.ZipFile(sanitized_lib_path, 'w') as output_zf:
        for libraryjar in libraryjars:
            with zipfile.ZipFile(libraryjar, 'r') as input_zf:
                for zipinfo in input_zf.infolist():
                    if (not zipinfo.filename in program_entries and
                            not zipinfo.filename in library_entries):
                        library_entries.add(zipinfo.filename)
                        output_zf.writestr(zipinfo, input_zf.read(zipinfo))


def usage(argv, error):
    print(error)
    print("Usage: sanitize_libraries.py <sanitized_lib> <sanitized_pgconf> (" +
          "--injar <existing_injar>" + "|--libraryjar <existing_library_jar>" +
          "|--pgconf <existing_pgconf>)+")
    return 1


def main(argv):
    if (len(argv) < 4):
        return usage(argv, "Wrong number of arguments!")
    pgconfs = []
    injars = []
    libraryjars = []
    i = 2
    while i < len(argv):
        directive = argv[i]
        if directive not in ['--pgconf', '--injar', '--libraryjar']:
            return usage(
                argv,
                'Unexpected argument, expected one of --pgconf, --injar, and ' +
                '--libraryjar.')
        if i + 1 >= len(argv):
            return usage(argv, 'Expected argument after ' + directive + '.')
        file = argv[i + 1]
        if directive == '--pgconf':
            pgconfs.append(file)
        elif directive == '--injar':
            injars.append(file)
        elif directive == '--libraryjar':
            libraryjars.append(file)
        i = i + 2
    SanitizeLibrariesInPgconf(argv[0], argv[1], pgconfs, injars, libraryjars)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
