cmake_minimum_required(VERSION 3.22.1)

project(sevenx_node_bridge LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Runtime binaries are fetched by CI after their manifest hash is verified.
# Only the debug POC profile is linked here. Release tasks have a separate
# manifest gate and cannot package a POC runtime.
set(PROJECT_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(POC_RUNTIME_DIR "${PROJECT_ROOT}/runtime/poc/lite")
set(LIBNODE_PATH "${POC_RUNTIME_DIR}/${ANDROID_ABI}/libnode.so")
set(NODE_HEADERS "${POC_RUNTIME_DIR}/include/node")

if(NOT EXISTS "${LIBNODE_PATH}")
    message(FATAL_ERROR "Missing verified Node POC library: ${LIBNODE_PATH}. Run scripts/fetch-libnode.sh runtime/node-runtime-lite.json first.")
endif()

if(NOT EXISTS "${NODE_HEADERS}/node.h")
    message(FATAL_ERROR "Missing Node headers: ${NODE_HEADERS}/node.h")
endif()

add_library(node-runtime SHARED IMPORTED GLOBAL)
set_target_properties(node-runtime PROPERTIES
    IMPORTED_LOCATION "${LIBNODE_PATH}"
    IMPORTED_SONAME "libnode.so"
)

add_library(sevenx-node SHARED native-lib.cpp)
target_include_directories(sevenx-node PRIVATE "${NODE_HEADERS}")

find_library(android-log log)

target_link_libraries(sevenx-node PRIVATE node-runtime ${android-log})

# Keep our JNI bridge compatible with 16 KB Android page-size devices.
target_link_options(sevenx-node PRIVATE "-Wl,-z,max-page-size=16384")
