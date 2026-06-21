# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("embeddedjvm")

add_library(
        embeddedjvm
        SHARED
        EmbeddedJvm.cpp
        PraetorSecurity.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        embeddedjvm
        ${log-lib}
        dl)

# Fix for Android 15+ 16 KB page size compatibility (Compatible with CMake 3.10+)
set_target_properties(embeddedjvm PROPERTIES LINK_FLAGS "-Wl,-z,max-page-size=16384")
