# Minimum required version of CMake
cmake_minimum_required(VERSION 3.10.2)

# Project name
project(LlmApp)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add the jni directory to the include directories
include_directories(${CMAKE_SOURCE_DIR}/../jni)
include_directories(${CMAKE_SOURCE_DIR}/../jni/prebuilt/src)

# Specify the path to your prebuilt libraries
set(PREBUILT_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI})

# Add Sherpa libraries
add_library(sherpa_onnx SHARED IMPORTED)
set_target_properties(sherpa_onnx PROPERTIES IMPORTED_LOCATION
        ${PREBUILT_LIB_DIR}/libsherpa-onnx-jni.so)

# Add your source files
add_library(llm_jni SHARED
        mtk_llm_jni.cpp
)

# Find and link Android log library
find_library(log-lib log)

# Link against prebuilt libraries
target_link_libraries(llm_jni
        ${PREBUILT_LIB_DIR}/libcommon.so
        ${PREBUILT_LIB_DIR}/libmtk_llm.so
        ${PREBUILT_LIB_DIR}/libtokenizer.so
        ${PREBUILT_LIB_DIR}/libyaml-cpp.so
        ${PREBUILT_LIB_DIR}/libmain_llm.so
        ${PREBUILT_LIB_DIR}/libre2.so
        ${PREBUILT_LIB_DIR}/libsentencepiece.so
        sherpa_onnx
        ${log-lib}
)

# Set the output directory for the built library
set_target_properties(llm_jni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# Add this section to ensure symbols are properly exported
set_target_properties(llm_jni PROPERTIES
        LINK_FLAGS "-Wl,--no-as-needed")

# Add this to force the linker to include all symbols
target_link_options(llm_jni PRIVATE -Wl,--whole-archive ${PREBUILT_LIB_DIR}/libmtk_llm.so -Wl,--no-whole-archive)