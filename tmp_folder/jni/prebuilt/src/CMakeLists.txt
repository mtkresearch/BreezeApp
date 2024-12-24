# jni/prebuilt/src/CMakeLists.txt

# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Define the root path
set(ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

# Include directories
include_directories(${ROOT_PATH})

# Add the prebuilt common library
add_library(common SHARED IMPORTED)
set_target_properties(common PROPERTIES
        IMPORTED_LOCATION ${ROOT_PATH}/../libcommon.so
        INTERFACE_INCLUDE_DIRECTORIES ${ROOT_PATH}
)

# Add subdirectories for components
add_subdirectory(backend)
add_subdirectory(executor)
add_subdirectory(llm_helper)

# Build the mtk_llm shared library
add_library(mtk_llm SHARED
        mtk_llm.cpp
)

target_include_directories(mtk_llm
        PUBLIC
        ${ROOT_PATH}
)

target_link_libraries(mtk_llm
        PRIVATE
        backend
        executor
        llm_helper
        common
        log
        android
)