# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Add the backend library
add_library(backend STATIC
        backend.cpp
)

# Include directories
target_include_directories(backend
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against the common library
target_link_libraries(backend
        PUBLIC
        common
)