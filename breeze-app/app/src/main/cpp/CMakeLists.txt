cmake_minimum_required(VERSION 3.10.2)
project(LlmApp)

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Prebuilt libraries directory
set(PREBUILT_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI})


# Add proxy JNI wrapper (this just forwards to libllm_jni.so)
add_library(llm_jni_proxy SHARED
        llm_jni_proxy.cpp
)

# Link Android log lib
find_library(log-lib log)

# Add imported target for prebuilt libllm_jni.so
add_library(prebuilt_llm_jni SHARED IMPORTED)
set_target_properties(prebuilt_llm_jni PROPERTIES
        IMPORTED_LOCATION ${PREBUILT_LIB_DIR}/libllm_jni.so
)

# Link your proxy library against prebuilt libllm_jni + others
target_link_libraries(llm_jni_proxy
        prebuilt_llm_jni
        ${PREBUILT_LIB_DIR}/libcommon.so
        ${PREBUILT_LIB_DIR}/libmtk_llm.so
        ${PREBUILT_LIB_DIR}/libtokenizer.so
        ${PREBUILT_LIB_DIR}/libyaml-cpp.so
        ${PREBUILT_LIB_DIR}/libmain_llm.so
        ${PREBUILT_LIB_DIR}/libre2.so
        ${PREBUILT_LIB_DIR}/libsentencepiece.so
        ${log-lib}
)

# Output directory
set_target_properties(llm_jni_proxy PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}"
)

# Linker flags
set_target_properties(llm_jni_proxy PROPERTIES
        LINK_FLAGS "-Wl,--no-as-needed"
)

# Ensure libllm_jni symbols are included
target_link_options(llm_jni_proxy PRIVATE
        -Wl,--whole-archive ${PREBUILT_LIB_DIR}/libllm_jni.so -Wl,--no-whole-archive
)