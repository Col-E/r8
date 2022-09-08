#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import zipfile

def add_file_to_zip(file, destination, zip_file):
  with zipfile.ZipFile(zip_file, 'a') as zip:
    zip.write(file, destination)

def extract_all_that_matches(zip_file, destination, predicate):
  with zipfile.ZipFile(zip_file) as zip:
    names_to_extract = [name for name in zip.namelist() if predicate(name)]
    zip.extractall(path=destination, members=names_to_extract)
    return names_to_extract

def get_names_that_matches(zip_file, predicate):
  with zipfile.ZipFile(zip_file) as zip:
    return [name for name in zip.namelist() if predicate(name)]
