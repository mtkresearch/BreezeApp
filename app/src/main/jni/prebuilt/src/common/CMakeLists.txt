# jni/prebuilt/src/common/CMakeLists.txt

# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Add the common library
add_library(common SHARED
        dump.cpp
        logging.cpp
        file_mem_mapper.cpp
        file_source.cpp
        scope_profiling.cpp
)

# Include directories
target_include_directories(common
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against the log library
target_link_libraries(common
        PUBLIC
        log
)

# Set compile flags
target_compile_options(common
        PRIVATE
        -fvisibility=default
)

# Set the visibility of the symbols to default
set_target_properties(common PROPERTIES
        C_VISIBILITY_PRESET default
        CXX_VISIBILITY_PRESET default
)