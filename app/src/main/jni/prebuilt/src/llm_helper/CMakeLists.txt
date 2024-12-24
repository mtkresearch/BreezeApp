# jni/prebuilt/src/llm_helper/CMakeLists.txt

# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Add the llm_helper library
add_library(llm_helper STATIC
        ringbuffer_cache_manager.cpp
        rotary_embedding.cpp
        mask_builder.cpp
        token_embedding.cpp
        lora_weights_loader.cpp
)

# Include directories
target_include_directories(llm_helper
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Link against the common library
target_link_libraries(llm_helper
        PUBLIC
        common
)