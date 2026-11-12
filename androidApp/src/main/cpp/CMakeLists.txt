cmake_minimum_required(VERSION 3.22.1)
project(node_runtime CXX)

add_library(node_runtime SHARED native-lib.cpp)
include_directories(${CMAKE_SOURCE_DIR}/node/include/node)

add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libnode.so)

find_library(log-lib log)
target_link_libraries(node_runtime libnode ${log-lib} android)
