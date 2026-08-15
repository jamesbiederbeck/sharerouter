cmake_minimum_required(VERSION 3.22)
project(llama_jni)

# Path to a llama.cpp checkout that has already been configured/built for
# arm64-v8a (see ../NDK_BUILD.md). We link against its prebuilt libllama.so
# rather than add_subdirectory-ing the source, since llama.cpp lives outside
# this repo and this repo has no submodule/vendoring mechanism.
if(NOT DEFINED LLAMA_CPP_DIR)
    set(LLAMA_CPP_DIR "$ENV{HOME}/code/ml/llama.cpp")
endif()
set(LLAMA_CPP_BUILD_DIR "${LLAMA_CPP_DIR}/build-android")

add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
)

find_library(LLAMA_LIB llama PATHS "${LLAMA_CPP_BUILD_DIR}/bin" NO_DEFAULT_PATH NO_CMAKE_FIND_ROOT_PATH)
if(NOT LLAMA_LIB)
    message(FATAL_ERROR "libllama.so not found in ${LLAMA_CPP_BUILD_DIR}/bin — build llama.cpp for arm64-v8a first")
endif()

target_link_libraries(llama_jni PRIVATE "${LLAMA_LIB}" log)
