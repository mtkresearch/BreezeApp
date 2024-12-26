# Set the minimum required version of CMake
cmake_minimum_required(VERSION 3.22.1)

# Define the source files
set(MAIN_SOURCES
        main.cpp
#        main_batch_gen.cpp
#        main_spec_dec.cpp
#        main_medusa.cpp
)

# Create a static library instead of executables
add_library(main_lib STATIC ${MAIN_SOURCES})

# Include directories
target_include_directories(main_lib PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/..
        ${CMAKE_CURRENT_SOURCE_DIR}/../prebuilt/include
)

# Link libraries
target_link_libraries(main_lib
        utils
        llm_prebuilt
        common
        tokenizer
        ${log-lib}
)

# Expose the include directory
target_include_directories(main_lib PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})