cmake_minimum_required(VERSION 3.22.1)
project(harbuair_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Sender library: akustikrausch/airplay2-sender-cpp (Apache-2.0), pinned as a git
# submodule. Its CMake fetches Mbed TLS 3.6.0 (Apache-2.0) at configure time,
# so the FIRST Android build needs network access to github.com.
set(AIRPLAY_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(AIRPLAY_BUILD_TESTS   OFF CACHE BOOL "" FORCE)
set(AIRPLAY_BUILD_QT_HOST OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/airplay2-sender-cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/airplay2-sender-cpp)

add_library(harbuair_native SHARED native_engine.cpp)
target_link_libraries(harbuair_native PRIVATE raop_sender log)
