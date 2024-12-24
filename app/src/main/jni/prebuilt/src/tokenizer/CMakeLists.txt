# jni/prebuilt/src/tokenizer/CMakeLists.txt

# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Define paths
set(ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../..)
set(THIRD_PARTY_PATH ${ROOT_PATH}/third_party)

# Include prebuilt libraries
# Note: These might already be defined in the main prebuilt CMakeLists.txt
# If so, you can remove these definitions and just use the existing ones

# common library
add_library(common SHARED IMPORTED)
set_target_properties(common PROPERTIES
        IMPORTED_LOCATION ${ROOT_PATH}/libcommon.so
        INTERFACE_INCLUDE_DIRECTORIES ${ROOT_PATH}
)

# yaml-cpp library
add_library(yaml_cpp SHARED IMPORTED)
set_target_properties(yaml_cpp PROPERTIES
        IMPORTED_LOCATION ${THIRD_PARTY_PATH}/lib/libyaml-cpp.so
        INTERFACE_INCLUDE_DIRECTORIES "${ROOT_PATH};${THIRD_PARTY_PATH}/include"
)

# sentencepiece library
add_library(sentencepiece SHARED IMPORTED)
set_target_properties(sentencepiece PROPERTIES
        IMPORTED_LOCATION ${THIRD_PARTY_PATH}/lib/libsentencepiece.so
        INTERFACE_INCLUDE_DIRECTORIES ${ROOT_PATH}
)

# hf-tokenizer library
add_library(hf_tokenizer SHARED IMPORTED)
set_target_properties(hf_tokenizer PROPERTIES
        IMPORTED_LOCATION ${THIRD_PARTY_PATH}/lib/libhf-tokenizer.so
        INTERFACE_INCLUDE_DIRECTORIES ${ROOT_PATH}
)

# re2 library
add_library(re2 SHARED IMPORTED)
set_target_properties(re2 PROPERTIES
        IMPORTED_LOCATION ${THIRD_PARTY_PATH}/lib/libre2.so
        INTERFACE_INCLUDE_DIRECTORIES "${ROOT_PATH};${THIRD_PARTY_PATH}/include"
)

# Build tokenizer shared library
add_library(tokenizer SHARED
        tokenizer.cpp
        sentencepiece_tokenizer.cpp
        huggingface_tokenizer.cpp
        tiktoken_tokenizer.cpp
)

target_include_directories(tokenizer
        PUBLIC
        ${ROOT_PATH}
)

target_link_libraries(tokenizer
        PUBLIC
        common
        yaml_cpp
        re2
        sentencepiece
        hf_tokenizer
        log
)

target_compile_options(tokenizer
        PRIVATE
        -fexceptions
        -fvisibility=default
)

# Set the visibility of the symbols to default
set_target_properties(tokenizer PROPERTIES
        C_VISIBILITY_PRESET default
        CXX_VISIBILITY_PRESET default
)